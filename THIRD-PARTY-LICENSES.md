# Third-party licenses

Wellness Companion itself is licensed under the PolyForm Noncommercial
License 1.0.0 (see [LICENSE](LICENSE)). The components listed below are
redistributed with it under their own terms; nothing in this project's
license restricts or overrides the rights those licenses grant.

**This file is generated** by `installer/make_third_party_licenses.py`.
Regenerate it whenever dependencies change; do not edit it by hand.

## Bundled AI model

The desktop app downloads and loads **Qwen3-4B-Instruct-2507** in GGUF form
(`Qwen3-4B-Instruct-2507-Q4_K_M.gguf`, quantised by lmstudio-community).
Qwen3 is released by Alibaba Cloud under the **Apache License 2.0**.
The model is not stored in this repository; it is fetched at install time
from <https://huggingface.co/lmstudio-community/Qwen3-4B-Instruct-2507-GGUF>.

## Bundled font

**Nunito** by Vernon Adams, Cyreal and Jacques Le Bailly, under the
**SIL Open Font License 1.1** (<https://openfontlicense.org>). The latin and
latin-ext subsets are vendored at
`windows/src/renderer/src/assets/fonts/` so the app makes no network request
for fonts at runtime.

## Runtime components installed alongside the app

The installer bundles the **Microsoft Visual C++ Redistributable**, which is
redistributable under the Microsoft Visual Studio redistributable terms.

## Desktop app — npm dependencies (152 production packages)

### (BSD-2-Clause OR MIT OR Apache-2.0)

| package | version |
|---|---|
| `rc` | 1.2.8 |

### (MIT OR WTFPL)

| package | version |
|---|---|
| `expand-template` | 2.0.3 |

### Apache-2.0

| package | version |
|---|---|
| `detect-libc` | 2.1.2 |
| `tunnel-agent` | 0.6.0 |
| `typescript` | 5.9.3 |

### BSD-3-Clause

| package | version |
|---|---|
| `ieee754` | 1.2.1 |

### BlueOak-1.0.0

| package | version |
|---|---|
| `chownr` | 3.0.0 |
| `isexe` | 4.0.0 |
| `minipass` | 7.1.3 |
| `tar` | 7.5.15 |
| `yallist` | 5.0.0 |

### ISC

| package | version |
|---|---|
| `@isaacs/fs-minipass` | 4.0.1 |
| `chownr` | 1.1.4 |
| `cliui` | 8.0.1 |
| `get-caller-file` | 2.0.5 |
| `graceful-fs` | 4.2.11 |
| `inherits` | 2.0.4 |
| `ini` | 1.3.8 |
| `isexe` | 2.0.0 |
| `lucide-react` | 1.31.0 |
| `once` | 1.4.0 |
| `semver` | 7.8.1 |
| `signal-exit` | 3.0.7, 4.1.0 |
| `validate-npm-package-name` | 7.0.2 |
| `which` | 2.0.2, 6.0.1 |
| `wrappy` | 1.0.2 |
| `y18n` | 5.0.8 |
| `yargs-parser` | 21.1.1 |

### MIT

| package | version |
|---|---|
| `@huggingface/jinja` | 0.5.9 |
| `@kwsites/file-exists` | 1.1.1 |
| `@kwsites/promise-deferred` | 1.1.1 |
| `@leichtgewicht/ip-codec` | 2.0.5 |
| `@node-llama-cpp/win-arm64` | 3.18.1 |
| `@node-llama-cpp/win-x64` | 3.18.1 |
| `@node-llama-cpp/win-x64-cuda` | 3.18.1 |
| `@node-llama-cpp/win-x64-cuda-ext` | 3.18.1 |
| `@node-llama-cpp/win-x64-vulkan` | 3.18.1 |
| `@reflink/reflink` | 0.1.19 |
| `@reflink/reflink-win32-x64-msvc` | 0.1.19 |
| `@simple-git/args-pathspec` | 1.0.3 |
| `@simple-git/argv-parser` | 1.1.1 |
| `@tinyhttp/content-disposition` | 2.2.4 |
| `ansi-escapes` | 6.2.1 |
| `ansi-regex` | 5.0.1, 6.2.2 |
| `ansi-styles` | 4.3.0, 6.2.3 |
| `async-retry` | 1.3.3 |
| `base64-js` | 1.5.1 |
| `better-sqlite3-multiple-ciphers` | 12.11.1 |
| `bindings` | 1.5.0 |
| `bl` | 4.1.0 |
| `bonjour-service` | 1.4.0 |
| `buffer` | 5.7.1 |
| `bytes` | 3.1.2 |
| `chalk` | 5.6.2 |
| `chmodrp` | 1.0.2 |
| `ci-info` | 4.4.0 |
| `cli-cursor` | 5.0.0 |
| `cli-spinners` | 2.9.2, 3.4.0 |
| `cmake-js` | 8.0.0 |
| `color-convert` | 2.0.1 |
| `color-name` | 1.1.4 |
| `commander` | 10.0.1 |
| `cookie` | 1.1.1 |
| `cross-spawn` | 7.0.6 |
| `date-fns` | 4.3.0 |
| `debug` | 4.4.3 |
| `decompress-response` | 6.0.0 |
| `deep-extend` | 0.6.0 |
| `dns-packet` | 5.6.1 |
| `emoji-regex` | 8.0.0, 10.6.0 |
| `end-of-stream` | 1.4.5 |
| `env-var` | 7.5.0 |
| `escalade` | 3.2.0 |
| `eventemitter3` | 5.0.4 |
| `fast-deep-equal` | 3.1.3 |
| `file-uri-to-path` | 1.0.0 |
| `filename-reserved-regex` | 3.0.0 |
| `filenamify` | 6.0.0 |
| `fs-constants` | 1.0.0 |
| `fs-extra` | 11.3.5 |
| `get-east-asian-width` | 1.6.0 |
| `github-from-package` | 0.0.0 |
| `ignore` | 7.0.5 |
| `ipull` | 3.9.5 |
| `is-fullwidth-code-point` | 3.0.0, 5.1.0 |
| `is-interactive` | 2.0.0 |
| `is-unicode-supported` | 2.1.0 |
| `js-tokens` | 4.0.0 |
| `jsonfile` | 6.2.1 |
| `lifecycle-utils` | 2.1.0, 3.1.1 |
| `lodash.debounce` | 4.0.8 |
| `log-symbols` | 7.0.1 |
| `loose-envify` | 1.4.0 |
| `lowdb` | 7.0.1 |
| `mimic-function` | 5.0.1 |
| `mimic-response` | 3.1.0 |
| `minimist` | 1.2.8 |
| `minizlib` | 3.1.0 |
| `mkdirp-classic` | 0.5.3 |
| `ms` | 2.1.3 |
| `multicast-dns` | 7.2.5 |
| `nanoid` | 5.1.11 |
| `napi-build-utils` | 2.0.0 |
| `node-abi` | 3.92.0 |
| `node-addon-api` | 8.8.0 |
| `node-api-headers` | 1.9.0 |
| `node-llama-cpp` | 3.18.1 |
| `onetime` | 7.0.0 |
| `ora` | 9.4.0 |
| `parse-ms` | 3.0.0, 4.0.0 |
| `path-key` | 3.1.1 |
| `prebuild-install` | 7.1.3 |
| `pretty-bytes` | 6.1.1 |
| `pretty-ms` | 8.0.0, 9.3.0 |
| `proper-lockfile` | 4.1.2 |
| `pump` | 3.0.4 |
| `react` | 18.3.1 |
| `react-dom` | 18.3.1 |
| `react-router` | 7.15.1 |
| `react-router-dom` | 7.15.1 |
| `readable-stream` | 3.6.2 |
| `require-directory` | 2.1.1 |
| `restore-cursor` | 5.1.0 |
| `retry` | 0.12.0, 0.13.1 |
| `safe-buffer` | 5.2.1 |
| `scheduler` | 0.23.2 |
| `set-cookie-parser` | 2.7.2 |
| `shebang-command` | 2.0.0 |
| `shebang-regex` | 3.0.0 |
| `simple-concat` | 1.0.1 |
| `simple-get` | 4.0.1 |
| `simple-git` | 3.36.0 |
| `sleep-promise` | 9.1.0 |
| `slice-ansi` | 7.1.2, 8.0.0 |
| `stdin-discarder` | 0.3.2 |
| `stdout-update` | 4.0.1 |
| `steno` | 4.0.2 |
| `string-width` | 4.2.3, 7.2.0, 8.2.1 |
| `string_decoder` | 1.3.0 |
| `strip-ansi` | 6.0.1, 7.2.0 |
| `strip-json-comments` | 2.0.1 |
| `tar-fs` | 2.1.5 |
| `tar-stream` | 2.2.0 |
| `thunky` | 1.1.0 |
| `universalify` | 2.0.1 |
| `url-join` | 4.0.1 |
| `util-deprecate` | 1.0.2 |
| `uuid` | 11.1.1 |
| `wrap-ansi` | 7.0.0 |
| `ws` | 8.21.0 |
| `yargs` | 17.7.2 |
| `yoctocolors` | 2.1.2 |

## Phone app — Gradle dependencies (26 declared artifacts)

| artifact | license |
|---|---|
| `androidx.activity:activity-compose` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.compose.foundation:foundation` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.compose.material3:material3` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.compose.material:material-icons-extended` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.compose.ui:ui` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.compose.ui:ui-tooling` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.compose.ui:ui-tooling-preview` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.core:core-ktx` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.hilt:hilt-compiler` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.hilt:hilt-navigation-compose` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.hilt:hilt-work` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.lifecycle:lifecycle-runtime-compose` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.navigation:navigation-compose` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.room:room-compiler` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.room:room-ktx` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.room:room-runtime` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.sqlite:sqlite` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.sqlite:sqlite-android` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `androidx.work:work-runtime-ktx` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `com.google.code.gson:gson` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `com.google.dagger:hilt-android` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `com.google.dagger:hilt-android-compiler` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `com.squareup.okhttp3:okhttp` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `junit:junit` | [Eclipse Public License 1.0](https://www.eclipse.org/legal/epl-v10.html) |
| `net.zetetic:sqlcipher-android` | [SQLCipher Community Edition License (BSD-style)](https://www.zetetic.net/sqlcipher/license/) |

Transitive Android dependencies inherit the licenses of their parents;
the table lists what the project declares directly.
