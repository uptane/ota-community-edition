all: server-ca/certs/server-root.cert.pem root-ca/certs/root.cert.pem intermediate/certs/intermediate.cert.pem intermediate/certs/client.p12 ../nginx/server.crt ../nginx/server.key ../nginx/client-root.crt.pem

server-ca/crl:
	mkdir server-ca
	cp root-openssl.cnf server-ca/openssl.cnf
	mkdir server-ca/certs server-ca/crl server-ca/newcerts server-ca/private
	chmod 700 server-ca/private
	echo 1000 > server-ca/serial
	touch server-ca/index.txt

root-ca/crl:
	mkdir root-ca
	cp root-openssl.cnf root-ca/openssl.cnf
	mkdir root-ca/certs root-ca/crl root-ca/newcerts root-ca/private
	chmod 700 root-ca/private
	echo 1000 > root-ca/serial
	touch root-ca/index.txt

intermediate/crl:
	mkdir intermediate
	cp intermediate-openssl.cnf intermediate/openssl.cnf
	mkdir intermediate/certs intermediate/crl intermediate/newcerts intermediate/private intermediate/csr
	chmod 700 intermediate/private
	echo 1000 > intermediate/serial
	touch intermediate/index.txt
	echo 1000 > intermediate/crlnumber

# server root ca

server-ca/private/server-root.key.pem: server-ca/crl
	openssl genrsa -aes256 -passout pass:123456 -out $@.tmp 4096
	openssl rsa -passin pass:123456 -in $@.tmp -out $@
	rm -v $@.tmp

server-ca/private/server-root.csr: server-ca/private/server-root.key.pem
	openssl req -config server-ca/openssl.cnf -new -out $@ -key $< -subj "/C=GB/ST=London/L=London/O=Test SERVER CA/OU=Server CA/CN=localhost"

server-ca/certs/server-root.cert.pem: server-ca/private/server-root.key.pem server-ca/private/server-root.csr
	openssl x509 -req -days 7300 -in server-ca/private/server-root.csr -signkey $< -out $@ -extensions v3_req -extfile server-ca/openssl.cnf

# These specific openssl-pkcs12 parameters are required for java to use this p12 properly
server-ca/certs/server-root.cert.p12: server-ca/certs/server-root.cert.pem
	openssl pkcs12 -export -nokeys -passout pass: -out $@ -in $< -caname tuf-nginx -noiter -nomaciter

../nginx/server.crt: server-ca/certs/server-root.cert.pem
	cp $< ../nginx/server.crt

../nginx/server.key: server-ca/private/server-root.key.pem
	cp $< ../nginx/server.key

# root ca

root-ca/private/root.key.pem: root-ca/crl
	openssl genrsa -aes256 -passout pass:123456 -out $@ 4096

root-ca/certs/root.cert.pem: root-ca/private/root.key.pem
	openssl req -config root-ca/openssl.cnf -passin pass:123456 -key $< -new -x509 -days 7300 -sha256 -extensions v3_ca -out $@ -subj "/C=GB/ST=London/L=London/O=Test ROOT CA/OU=ROOT CA/CN=localhost"

../nginx/client-root.crt.pem: root-ca/certs/root.cert.pem
	cp $< ../nginx/client-root.crt.pem

# Intermediate ca

intermediate/private/intermediate.key.pem: intermediate/crl
	openssl genrsa -aes256 -passout pass:123456 -out $@ 4096
	chmod 400 $@

intermediate/csr/intermediate.csr.pem: intermediate/private/intermediate.key.pem
	openssl req -config intermediate/openssl.cnf -passin pass:123456 -new -sha256 -key $< -out $@ -subj "/C=GB/ST=London/L=London/O=Test Intermediate CA/OU=Intermediate CA/CN=localhost"

intermediate/certs/intermediate.cert.pem: intermediate/csr/intermediate.csr.pem root-ca/certs/root.cert.pem
	openssl ca -batch -config root-ca/openssl.cnf -passin pass:123456 -extensions v3_intermediate_ca -days 3650 -notext -md sha256 -in $< -out $@
	chmod 444 $@

# Client keys

intermediate/private/client.key.pem:  intermediate/certs/intermediate.cert.pem
	openssl genrsa -aes256 -passout pass:123456 -out $@ 2048
	chmod 400 $@

intermediate/csr/client.csr.pem: intermediate/private/client.key.pem
	openssl req -config intermediate/openssl.cnf -passin pass:123456 -key $< -new -sha256 -out $@ -subj "/C=PT/ST=Portugal/L=Lisbon/O=Test Client/OU=client-namespace/CN=localhost"

intermediate/certs/client.cert.pem: intermediate/csr/client.csr.pem
	openssl ca -batch -config intermediate/openssl.cnf -passin pass:123456 -extensions usr_cert -days 375 -notext -md sha256 -in $< -out $@
	chmod 444 $@

intermediate/certs/client.p12: intermediate/certs/client.cert.pem
	openssl pkcs12 -export -passin pass:123456 -passout pass: -out $@ -inkey intermediate/private/client.key.pem -in intermediate/certs/client.cert.pem -certfile intermediate/certs/intermediate.cert.pem

update-credentials.zip: intermediate/certs/client.p12 server-ca/certs/server-root.cert.p12
	cp $< /tmp/client_auth.p12
	cp server-ca/certs/server-root.cert.p12 /tmp/server.p12
	zip -j -u ../credentials_tls-auth_ed25519.zip /tmp/client_auth.p12
	zip -j -u ../credentials_tls-auth_ed25519.zip /tmp/server.p12

clean:
	rm -vrf server-ca root-ca intermediate

.PHONY: all clean update-credentials.zip
