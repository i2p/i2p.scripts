#! /usr/bin/env bash
openssl x509 -noout -fingerprint -sha256 -in <(openssl s_client -connect i2p.net:443) | grep "sha256 Fingerprint" | sed 's|sha256 Fingerprint=||g' > i2p.net.sha256
openssl x509 -noout -fingerprint -sha256 -in <(openssl s_client -connect geti2p.net:443) | grep "sha256 Fingerprint" | sed 's|sha256 Fingerprint=||g' > geti2p.net.sha256
openssl x509 -noout -fingerprint -sha256 -in <(openssl s_client -connect i2pgit.org:443) | grep "sha256 Fingerprint" | sed 's|sha256 Fingerprint=||g' > i2pgit.org.sha256
openssl x509 -noout -fingerprint -sha256 -in <(openssl s_client -connect deb.i2p2.de:443) | grep "sha256 Fingerprint" | sed 's|sha256 Fingerprint=||g' > deb.i2p2.de.sha256
openssl x509 -noout -fingerprint -sha256 -in <(openssl s_client -connect deb.i2p2.no:443) | grep "sha256 Fingerprint" | sed 's|sha256 Fingerprint=||g' > deb.i2p2.no.sha256
openssl x509 -noout -fingerprint -sha256 -in <(openssl s_client -connect i2pforum.net:443) | grep "sha256 Fingerprint" | sed 's|sha256 Fingerprint=||g' > i2pforum.net.sha256
