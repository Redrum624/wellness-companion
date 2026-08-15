"""Independent reference generator for shared/crypto-vectors.json.

Run: python scripts/gen-crypto-vectors.py

Deliberately uses a SEPARATE implementation path from both the Node
(windows/) and Kotlin (android/) code under test:
  - HKDF: a hand-rolled RFC 5869 Extract/Expand (plain hmac/hashlib), cross-
    checked against `cryptography`'s own HKDF class before anything is
    written out. If they disagree, the script aborts loudly.
  - AES-256-GCM: `cryptography`'s AESGCM, which already returns
    ciphertext||tag (the exact wire layout used on the Android side).
  - ECDH: `cryptography`'s SECP256R1, which returns the shared secret as a
    fixed-width big-endian X coordinate (leading zero bytes preserved).

The RFC 5869 SHA-256 test-case inputs/outputs (IKM/salt/info/L/PRK/OKM) are
taken verbatim from RFC 5869 Appendix A (fetched from rfc-editor.org). This
script re-derives PRK/OKM independently and asserts they match the published
values, so a transcription error in the published constants would fail loud
rather than silently poisoning both platforms' test suites.
"""
import base64
import hashlib
import hmac
import json
import os

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives.hashes import SHA256

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_PATH = os.path.join(REPO_ROOT, "shared", "crypto-vectors.json")


# ---------------------------------------------------------------------------
# Hand-rolled RFC 5869 HKDF (independent of the Node/Kotlin implementations)
# ---------------------------------------------------------------------------
def hkdf_extract(salt: bytes, ikm: bytes) -> bytes:
    return hmac.new(salt, ikm, hashlib.sha256).digest()


def hkdf_expand(prk: bytes, info: bytes, length: int) -> bytes:
    t, okm, i = b"", b"", 1
    while len(okm) < length:
        t = hmac.new(prk, t + info + bytes([i]), hashlib.sha256).digest()
        okm += t
        i += 1
    return okm[:length]


def hkdf_full_cryptography(salt: bytes, ikm: bytes, info: bytes, length: int) -> bytes:
    """Cross-check via `cryptography`'s own HKDF (extract+expand combined)."""
    return HKDF(algorithm=SHA256(), length=length, salt=salt or None, info=info).derive(ikm)


# ---------------------------------------------------------------------------
# Section 1: RFC 5869 SHA-256 test vectors (verbatim inputs/outputs)
# ---------------------------------------------------------------------------
RFC5869_CASES = [
    {
        "name": "RFC 5869 A.1 - basic",
        "IKM": bytes([0x0B] * 22),
        "salt": bytes.fromhex("000102030405060708090a0b0c"),
        "info": bytes.fromhex("f0f1f2f3f4f5f6f7f8f9"),
        "L": 42,
        "PRK": "077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5",
        "OKM": "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
    },
    {
        "name": "RFC 5869 A.2 - longer inputs/outputs",
        "IKM": bytes(range(0x00, 0x00 + 80)),
        "salt": bytes(range(0x60, 0x60 + 80)),
        "info": bytes(range(0xB0, 0xB0 + 80)),
        "L": 82,
        "PRK": "06a6b88c5853361a06104c9ceb35b45cef760014904671014a193f40c15fc244",
        "OKM": (
            "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c59045a99cac7827"
            "271cb41c65e590e09da3275600c2f09b8367793a9aca3db71cc30c58179ec3e87c14c01d5c1f3434f1d87"
        ),
    },
    {
        "name": "RFC 5869 A.3 - zero-length salt/info",
        "IKM": bytes([0x0B] * 22),
        "salt": b"",
        "info": b"",
        "L": 42,
        "PRK": "19ef24a32c717b167f33a91d6f648bdf96596776afdb6377ac434c1c293ccb04",
        "OKM": "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
    },
]

hkdf_rfc5869 = []
for case in RFC5869_CASES:
    prk = hkdf_extract(case["salt"], case["IKM"])
    okm = hkdf_expand(prk, case["info"], case["L"])
    okm_cross = hkdf_full_cryptography(case["salt"], case["IKM"], case["info"], case["L"])

    assert prk.hex() == case["PRK"], f"{case['name']}: PRK mismatch: got {prk.hex()} want {case['PRK']}"
    assert okm.hex() == case["OKM"], f"{case['name']}: OKM mismatch: got {okm.hex()} want {case['OKM']}"
    assert okm == okm_cross, f"{case['name']}: hand-rolled HKDF disagrees with cryptography's HKDF"

    hkdf_rfc5869.append(
        {
            "IKM": case["IKM"].hex(),
            "salt": case["salt"].hex(),
            "info": case["info"].hex(),
            "L": case["L"],
            "PRK": prk.hex(),
            "OKM": okm.hex(),
        }
    )

print(f"[ok] hkdf_rfc5869: {len(hkdf_rfc5869)} vectors match published RFC 5869 values")


# ---------------------------------------------------------------------------
# Section 2: full handshake derivation vector
# Contract (pinned, both platforms MUST match):
#   prk   = HKDF-Extract(salt=th, ikm = ss || psk)
#   km    = HKDF-Expand(prk, "confirm", 32)
#   mac_s = HMAC-SHA256(km, "srv" || th)
#   mac_c = HMAC-SHA256(km, "cli" || th)
#   k_c2s = HKDF-Expand(prk, "c2s", 32)
#   k_s2c = HKDF-Expand(prk, "s2c", 32)
# `th` here is a fixed opaque 32-byte value (not re-derived from wire bytes
# in this fixture) -- it stands in for whatever transcript hash the real
# handshake computed; T1/T2 test the wire-bytes-to-th construction itself.
# ---------------------------------------------------------------------------
ss = bytes(range(32))            # PIN: fixed, reproducible (0x00..0x1f)
psk = bytes(range(32, 64))       # PIN: fixed, reproducible (0x20..0x3f)
th = hashlib.sha256(b"transcript-fixture").digest()

prk = hkdf_extract(th, ss + psk)
km = hkdf_expand(prk, b"confirm", 32)
mac_s = hmac.new(km, b"srv" + th, hashlib.sha256).digest()
mac_c = hmac.new(km, b"cli" + th, hashlib.sha256).digest()
k_c2s = hkdf_expand(prk, b"c2s", 32)
k_s2c = hkdf_expand(prk, b"s2c", 32)

# Cross-check the handshake KDF chain against cryptography's HKDF too.
prk_cross_input_check = hkdf_full_cryptography(th, ss + psk, b"confirm", 32)
assert km == prk_cross_input_check, "handshake: km disagrees with cryptography's combined HKDF(salt=th,info='confirm')"

handshake = {
    "ss": ss.hex(),
    "psk": psk.hex(),
    "th": th.hex(),
    "prk": prk.hex(),
    "km": km.hex(),
    "mac_s": mac_s.hex(),
    "mac_c": mac_c.hex(),
    "k_c2s": k_c2s.hex(),
    "k_s2c": k_s2c.hex(),
}
print("[ok] handshake vector derived and cross-checked")


# ---------------------------------------------------------------------------
# Section 3: GCM record vector
# nonce = dir(4B) || counter(8B BE); AAD = byte0(0x01) || counter(8B BE)
# ct_tag = AESGCM ciphertext with the 16B tag appended (Android-native layout)
# ---------------------------------------------------------------------------
gcm_key = hashlib.sha256(b"gcm-record-fixture-key").digest()  # 32 bytes
direction = bytes.fromhex("63327300")  # arbitrary fixed 4-byte direction constant ("c2s\0")
counter = (0).to_bytes(8, "big")
gcm_nonce = direction + counter
assert len(gcm_nonce) == 12
gcm_aad = bytes([0x01]) + counter
assert len(gcm_aad) == 9
gcm_plaintext = b'{"type":"auth_ok"}'

aesgcm = AESGCM(gcm_key)
ct_tag = aesgcm.encrypt(gcm_nonce, gcm_plaintext, gcm_aad)
assert len(ct_tag) == len(gcm_plaintext) + 16

# Round-trip decrypt as an independent sanity check.
recovered = aesgcm.decrypt(gcm_nonce, ct_tag, gcm_aad)
assert recovered == gcm_plaintext

gcm_record = {
    "key": gcm_key.hex(),
    "nonce": gcm_nonce.hex(),
    "aad": gcm_aad.hex(),
    "plaintext": gcm_plaintext.hex(),
    "ct_tag": ct_tag.hex(),
}
print("[ok] gcm_record vector generated and round-tripped")


# ---------------------------------------------------------------------------
# Section 4: P-256 ECDH keypair whose shared-secret X has a leading 0x00 byte
# ---------------------------------------------------------------------------
def gen_p256_keypair():
    priv = ec.generate_private_key(ec.SECP256R1())
    return priv, priv.public_key()


tries = 0
while True:
    tries += 1
    priv_a, pub_a = gen_p256_keypair()
    priv_b, pub_b = gen_p256_keypair()
    ss_ab = priv_a.exchange(ec.ECDH(), pub_b)
    assert len(ss_ab) == 32, f"unexpected shared-secret length {len(ss_ab)}"
    if ss_ab[0] == 0x00:
        # Confirm both directions agree (real ECDH property) before pinning.
        ss_ba = priv_b.exchange(ec.ECDH(), pub_a)
        assert ss_ab == ss_ba
        break

print(f"[ok] found leading-zero-X P-256 keypair after {tries} tries: ss[0:4]={ss_ab[:4].hex()}")

priv_a_pkcs8_b64 = base64.b64encode(
    priv_a.private_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )
).decode()
pub_b_spki_b64 = base64.b64encode(
    pub_b.public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
).decode()

ecdh_leading_zero_x = {
    "priv_a": priv_a_pkcs8_b64,
    "pub_b_spki": pub_b_spki_b64,
    "expected_ss": ss_ab.hex(),
}
assert ecdh_leading_zero_x["expected_ss"].startswith("00"), "expected_ss must retain its leading zero byte"


# ---------------------------------------------------------------------------
# Section 5: off-curve SPKI blob (negative test)
# Start from a valid SPKI DER, corrupt the point so it's no longer on P-256,
# and confirm the reference library itself rejects it on import.
# ---------------------------------------------------------------------------
base_priv, base_pub = gen_p256_keypair()
valid_spki = base_pub.public_bytes(
    encoding=serialization.Encoding.DER,
    format=serialization.PublicFormat.SubjectPublicKeyInfo,
)

offcurve = None
candidate = bytearray(valid_spki)
for flip_index in range(1, len(candidate) + 1):
    trial = bytearray(valid_spki)
    trial[-flip_index] ^= 0xFF
    try:
        serialization.load_der_public_key(bytes(trial))
        # Still parses (and, in principle, could still land on-curve by
        # freak chance) -- keep trying other byte positions.
        continue
    except Exception:
        offcurve = bytes(trial)
        break

assert offcurve is not None, "could not construct an off-curve SPKI blob that the reference library rejects"
offcurve_spki_b64 = base64.b64encode(offcurve).decode()
print("[ok] offcurve_spki constructed and confirmed rejected by cryptography's loader")


# ---------------------------------------------------------------------------
# Assemble and write
# ---------------------------------------------------------------------------
out = {
    "hkdf_rfc5869": hkdf_rfc5869,
    "handshake": handshake,
    "gcm_record": gcm_record,
    "ecdh_leading_zero_x": ecdh_leading_zero_x,
    "offcurve_spki": offcurve_spki_b64,
}

os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
with open(OUT_PATH, "w", encoding="utf-8") as f:
    json.dump(out, f, indent=2)
    f.write("\n")

print(f"[ok] wrote {OUT_PATH}")
