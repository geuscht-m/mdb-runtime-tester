# !/bin/sh
#
# Generates a set of TLS certificates for use with the test VMs.
# Only needs to be run once before running vagrant up as the
# Vagrantfile will copy the files to the destination VMs.
#
mkdir -p tls/CA
mkdir -p tls/testdriver
mkdir -p tls/rs1 tls/rs2 tls/rs3

# Generate the root cert
#openssl genrsa -aes256 -out tls/CA/root.key 4096
openssl req -x509 -new -nodes -keyout tls/CA/root.key -sha256 -days 365 -out tls/CA/root.crt -subj "/C=US/ST=NY/L=NYC/O=TEST/CN=auth.mongodb.test"

# Generate node keys

for i in 1 2 3
do
    openssl req -new -nodes -keyout tls/rs$i/rs$i.key -out tls/rs$i/rs$i.csr -days 365 -subj "/C=US/ST=NY/L=NYC/O=TEST/OU=Server/CN=rs$i.mongodb.test"
    openssl x509 -req -days 365 -sha256 -in tls/rs$i/rs$i.csr -CA tls/CA/root.crt -CAkey tls/CA/root.key -CAcreateserial -out tls/rs$i/rs$i.crt
done

openssl req -new -nodes -keyout tls/testdriver/testdriver.key -out tls/testdriver/testdriver.csr -days 365 -subj "/C=US/ST=NY/L=NYC/O=TEST/OU=Server/CN=testdriver.mongodb.test"
openssl x509 -req -days 365 -sha256 -in tls/testdriver/testdriver.csr -CA tls/CA/root.crt -CAkey tls/CA/root.key -CAcreateserial -out tls/testdriver/testdriver.crt
