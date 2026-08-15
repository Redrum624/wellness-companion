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
# Section 2b: psk_from_secret -- promoting the pairing secret to the PSK
#
# The `handshake` section above takes `psk` as an INPUT, so it pins everything
# downstream of the PSK but says nothing about how the PSK is produced from the
# 128-bit pairing secret. That left one derivation step where each platform
# could only check its own implementation against its own HKDF -- which proves
# nothing cross-platform, and a divergence would present as "the code you typed
# is wrong" with no other symptom.
#
# Contract (pinned, both platforms MUST match):
#   psk = HKDF-SHA256(ikm = secret(16B), salt = EMPTY, info = "wc-psk", 32)
#
# `salt = empty` is RFC 5869's "not provided" case: HMAC zero-pads any key
# shorter than its 64-byte block, so an empty salt and a 32-byte zero salt are
# the same HMAC key. That equivalence is already exercised by RFC 5869 A.3
# above, so the three implementations cannot disagree about it silently.
# ---------------------------------------------------------------------------
psk_secret = bytes.fromhex("4f1c9a37e0b5d284c6317fa8905e2db3")  # PIN: fixed 16 bytes
assert len(psk_secret) == 16, "the pairing secret floor is 128 bits"

psk_derived = hkdf_expand(hkdf_extract(b"", psk_secret), b"wc-psk", 32)
psk_cross = hkdf_full_cryptography(b"", psk_secret, b"wc-psk", 32)
assert psk_derived == psk_cross, "psk_from_secret: hand-rolled HKDF disagrees with cryptography's HKDF"
assert len(psk_derived) == 32
# A different secret must give a different PSK (catches a dropped ikm).
assert psk_derived != hkdf_expand(hkdf_extract(b"", bytes(16)), b"wc-psk", 32)
# The info label is load-bearing (catches a dropped/renamed label).
assert psk_derived != hkdf_expand(hkdf_extract(b"", psk_secret), b"", 32)

psk_from_secret = {
    "rule": 'psk = HKDF-SHA256(ikm=secret, salt=empty, info="wc-psk", len=32)',
    "info": "wc-psk",
    "salt": "",
    "length": 32,
    "secret_hex": psk_secret.hex(),
    "expected_psk_hex": psk_derived.hex(),
}
print("[ok] psk_from_secret vector derived and cross-checked")


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
# Section 3b: GCM record vector for the SERVER->CLIENT direction
# Same framing rule, the other direction constant. Without this, `DIR_S2C`
# ("s2c\0") was pinned only by a literal inside the desktop's own test suite,
# so a mirrored implementation could get the s2c constant wrong and only find
# out at the cross-platform interop gate. The counter is deliberately NON-ZERO
# here so the uint64 big-endian encoding is pinned in BOTH the nonce and the
# AAD (the all-zero counter in Section 3 cannot distinguish BE from LE).
# ---------------------------------------------------------------------------
gcm_key_s2c = hashlib.sha256(b"gcm-record-fixture-key-s2c").digest()  # 32 bytes
direction_s2c = bytes.fromhex("73326300")  # "s2c\0"
counter_s2c = (1).to_bytes(8, "big")
gcm_nonce_s2c = direction_s2c + counter_s2c
assert len(gcm_nonce_s2c) == 12
gcm_aad_s2c = bytes([0x01]) + counter_s2c
assert len(gcm_aad_s2c) == 9
gcm_plaintext_s2c = b'{"type":"push_ack","inserted":1,"updated":0,"rejected":0}'

aesgcm_s2c = AESGCM(gcm_key_s2c)
ct_tag_s2c = aesgcm_s2c.encrypt(gcm_nonce_s2c, gcm_plaintext_s2c, gcm_aad_s2c)
assert len(ct_tag_s2c) == len(gcm_plaintext_s2c) + 16
assert aesgcm_s2c.decrypt(gcm_nonce_s2c, ct_tag_s2c, gcm_aad_s2c) == gcm_plaintext_s2c

# The two directions must not collide: different key AND different nonce.
assert gcm_nonce_s2c != gcm_nonce
assert gcm_key_s2c != gcm_key

gcm_record_s2c = {
    "key": gcm_key_s2c.hex(),
    "nonce": gcm_nonce_s2c.hex(),
    "aad": gcm_aad_s2c.hex(),
    "counter": 1,
    "plaintext": gcm_plaintext_s2c.hex(),
    "ct_tag": ct_tag_s2c.hex(),
}
print("[ok] gcm_record_s2c vector generated and round-tripped")


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

# NOTE on reproducibility: sections 4 and 5 above (and the two throwaway
# keypairs used in section 6 below) come from an UNSEEDED random search /
# unseeded key generation. Re-running this script produces different, but
# equally valid, key material and byte content for those fields every time
# -- the committed shared/crypto-vectors.json is the pinned snapshot; a
# future "regenerate and diff" against it is EXPECTED to show a full diff
# on these fields and is not, by itself, evidence of a regression. Only the
# hkdf_rfc5869 / handshake / gcm_record sections (all derived from fixed
# literals) are byte-for-byte reproducible across runs.


# ---------------------------------------------------------------------------
# Section 6: th (transcript hash) wire-byte construction
# th = SHA256(len-prefixed wire bytes: hello || hs1 || pub_s_b64), each
# element prefixed with its length as a uint32 BIG-ENDIAN integer.
# Pinned choice: pub_s_b64 contributes its ASCII/base64 TEXT bytes (the
# literal characters the server put in the JSON), NOT the decoded SPKI DER
# bytes -- this is spec §2.3's explicit call-out and the #1 way Node/Android
# could silently diverge (one side hashing base64 text, the other hashing
# decoded DER).
# ---------------------------------------------------------------------------
def uint32be_len_prefixed_concat(elements: list) -> bytes:
    parts = []
    for e in elements:
        parts.append(len(e).to_bytes(4, "big"))
        parts.append(e)
    return b"".join(parts)


def deterministic_p256_private_key(label: bytes):
    """Deterministic P-256 private key derived from `label`, without
    hardcoding the curve order: try successive SHA-256(label||counter)
    scalars until one falls in cryptography's accepted [1, n-1] range."""
    counter = 0
    while True:
        d = int.from_bytes(hashlib.sha256(label + counter.to_bytes(4, "big")).digest(), "big")
        try:
            return ec.derive_private_key(d, ec.SECP256R1())
        except ValueError:
            counter += 1


nonce_s = hashlib.sha256(b"th-fixture-nonce_s").digest()
nonce_c = hashlib.sha256(b"th-fixture-nonce_c").digest()
pub_c_key = deterministic_p256_private_key(b"th-fixture-pub_c").public_key()
pub_s_key = deterministic_p256_private_key(b"th-fixture-pub_s").public_key()


def spki_b64(pub_key) -> str:
    return base64.b64encode(
        pub_key.public_bytes(
            encoding=serialization.Encoding.DER,
            format=serialization.PublicFormat.SubjectPublicKeyInfo,
        )
    ).decode()


hello_obj = {
    "type": "hello",
    "version": 4,
    "minVersion": 4,
    "crypto": ["x1"],
    "nonce_s": base64.b64encode(nonce_s).decode(),
}
hello_json = json.dumps(hello_obj, separators=(",", ":"))

hs1_obj = {
    "type": "hs1",
    "proto": "x1",
    "keyId": "11111111-1111-4111-8111-111111111111",
    "deviceId": "22222222-2222-4222-8222-222222222222",
    "pub_c": spki_b64(pub_c_key),
    "nonce_c": base64.b64encode(nonce_c).decode(),
}
hs1_json = json.dumps(hs1_obj, separators=(",", ":"))

pub_s_b64 = spki_b64(pub_s_key)

# Order matters: hello, then hs1, then pub_s_b64 (spec §2.3).
th_elements = [hello_json.encode("utf-8"), hs1_json.encode("utf-8"), pub_s_b64.encode("ascii")]
expected_th = hashlib.sha256(uint32be_len_prefixed_concat(th_elements)).digest()

th_wire_bytes = {
    "hello_json": hello_json,
    "hs1_json": hs1_json,
    "pub_s_b64": pub_s_b64,
    "elements_hex": [e.hex() for e in th_elements],
    "expected_th": expected_th.hex(),
}
print("[ok] th_wire_bytes constructed: uint32BE-length-prefixed(hello, hs1, pub_s_b64-as-text)")


# ---------------------------------------------------------------------------
# Section 7: pairing code (spec §2.2 as amended 2026-08-15)
#
# The user transcribes ONE string. It carries the lookup handle AND the secret:
#
#   bytes    : keyId(4) || secret(16)              = 20 bytes, in that order
#   number   : big-endian integer over those 20 bytes
#   alphabet : "ABCDEFGHJKMNPQRSTUVWXYZ23456789"   31 glyphs, index 0..30,
#              excluding 0/O/1/I/L (the pairs people misread off a screen)
#   encode   : base-31, MOST significant glyph first, LEFT-PADDED to exactly
#              33 glyphs (31^33 > 2^160, so 20 bytes always fit and the length
#              is fixed -- the all-zero value renders as 33 'A's, never "").
#   display  : grouped 5-5-5-5-5-5-3 with '-' separators -> 39 chars on screen
#   decode   : strip [\s-], upper-case, require exactly 33 glyphs, accumulate
#              n = n*31 + index(ch), REJECT n >= 2**160, render as 20 bytes;
#              keyId = bytes[0:4] as 8 LOWERCASE hex (exactly the `hs1` field),
#              secret = bytes[4:20] (16 bytes / 128 bits -- that floor is fixed;
#              keyId carries no entropy requirement and may collide harmlessly).
#
# Encoded on the desktop, decoded on the phone: a divergence here strands the
# user mid-pairing with no diagnostic, which is exactly what this fixture is
# for. Implemented independently below (plain int arithmetic) and asserted
# against in Jest and JUnit.
# ---------------------------------------------------------------------------
PAIRING_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
PAIRING_CODE_LENGTH = 33
PAIRING_KEY_ID_BYTES = 4
PAIRING_SECRET_BYTES = 16
PAIRING_GROUP = 5
assert len(PAIRING_ALPHABET) == 31
assert 31 ** PAIRING_CODE_LENGTH > 2 ** ((PAIRING_KEY_ID_BYTES + PAIRING_SECRET_BYTES) * 8)


def encode_pairing_code(key_id_hex: str, secret_hex: str) -> tuple:
    assert len(key_id_hex) == PAIRING_KEY_ID_BYTES * 2
    assert len(secret_hex) == PAIRING_SECRET_BYTES * 2
    n = int(key_id_hex + secret_hex, 16)
    glyphs = []
    for _ in range(PAIRING_CODE_LENGTH):
        glyphs.append(PAIRING_ALPHABET[n % 31])
        n //= 31
    assert n == 0, "20 bytes overflowed 33 glyphs"
    bare = "".join(reversed(glyphs))
    groups = [bare[i:i + PAIRING_GROUP] for i in range(0, len(bare), PAIRING_GROUP)]
    return "-".join(groups), bare


def decode_pairing_code(code: str) -> tuple:
    cleaned = "".join(ch for ch in code.upper() if not ch.isspace() and ch != "-")
    assert len(cleaned) == PAIRING_CODE_LENGTH, f"expected {PAIRING_CODE_LENGTH} glyphs"
    n = 0
    for ch in cleaned:
        n = n * 31 + PAIRING_ALPHABET.index(ch)
    assert n < 2 ** ((PAIRING_KEY_ID_BYTES + PAIRING_SECRET_BYTES) * 8), "code out of range"
    raw = n.to_bytes(PAIRING_KEY_ID_BYTES + PAIRING_SECRET_BYTES, "big")
    return raw[:PAIRING_KEY_ID_BYTES].hex(), raw[PAIRING_KEY_ID_BYTES:].hex()


PAIRING_CASES = [
    # A "typical" code: distinct keyId, sequential secret bytes.
    ("deadbeef", bytes(range(16)).hex()),
    # All zeroes: proves the fixed-width left-pad (a naive encoder emits "").
    ("00" * PAIRING_KEY_ID_BYTES, "00" * PAIRING_SECRET_BYTES),
    # All ones: the largest 20-byte value, proves 33 glyphs never overflow.
    ("ff" * PAIRING_KEY_ID_BYTES, "ff" * PAIRING_SECRET_BYTES),
]

pairing_vectors = []
for key_id_hex, secret_hex in PAIRING_CASES:
    grouped, bare = encode_pairing_code(key_id_hex, secret_hex)
    assert len(bare) == PAIRING_CODE_LENGTH
    assert [len(g) for g in grouped.split("-")] == [5, 5, 5, 5, 5, 5, 3]
    # Round-trip through the normalization the phone applies: dashes stripped,
    # arbitrary case, surrounding whitespace.
    for variant in (grouped, bare, bare.lower(), f"  {grouped.lower()}  "):
        assert decode_pairing_code(variant) == (key_id_hex, secret_hex), f"round-trip failed for {variant!r}"
    pairing_vectors.append(
        {
            "keyId_hex": key_id_hex,
            "secret_hex": secret_hex,
            "code_grouped": grouped,
            "code_bare": bare,
        }
    )

pairing_code = {
    "alphabet": PAIRING_ALPHABET,
    "code_length": PAIRING_CODE_LENGTH,
    "key_id_bytes": PAIRING_KEY_ID_BYTES,
    "secret_bytes": PAIRING_SECRET_BYTES,
    "grouping": [5, 5, 5, 5, 5, 5, 3],
    "byte_order": "keyId||secret, big-endian base31, most-significant glyph first",
    "decode_normalization": "strip [\\s-], upper-case, require 33 glyphs, reject n >= 2^160",
    "vectors": pairing_vectors,
}
print(f"[ok] pairing_code: {len(pairing_vectors)} vectors encoded and round-tripped")


# ---------------------------------------------------------------------------
# Assemble and write
# ---------------------------------------------------------------------------
out = {
    "hkdf_rfc5869": hkdf_rfc5869,
    "handshake": handshake,
    "psk_from_secret": psk_from_secret,
    "gcm_record": gcm_record,
    "gcm_record_s2c": gcm_record_s2c,
    "ecdh_leading_zero_x": ecdh_leading_zero_x,
    "offcurve_spki": offcurve_spki_b64,
    "th_wire_bytes": th_wire_bytes,
    "pairing_code": pairing_code,
}

os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
with open(OUT_PATH, "w", encoding="utf-8") as f:
    json.dump(out, f, indent=2)
    f.write("\n")

print(f"[ok] wrote {OUT_PATH}")
