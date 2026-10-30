# Offline /s/ resolver security checks

With a JDK 11 or newer available through `JAVA_HOME` or `PATH`, run:

```sh
uv run test/test_slink_security.py
```

Alternatively, pass `--java-home /path/to/jdk`. The runner has no Python package
dependencies and requires neither an Android SDK nor GitHub Packages credentials.
It compiles the production resolver with temporary, test-only Android/Morphe
stubs and runs both Java test programs. Nothing is installed or sent over the network.

The checks cover legitimate share links, malicious hostnames and URL components,
HTTPS and port restrictions, redirects, and the `#bypass` fallback. A fake HTTPS
transport exercises the production credential method: rejected destinations must
not open a connection or reach the Android navigation code, while validated Reddit
requests retain their token, method, timeouts and disabled automatic redirects.

These checks supplement patching a real APK and testing on Android. They do not
verify the Android WebView login flow or exercise a live Reddit account.
