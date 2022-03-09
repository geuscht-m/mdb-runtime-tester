require 'fileutils'

module CommonSetupFunctions
  
  def self.createBaseDirs(max_rs)
    if not File.directory? 'tls'
      Dir.mkdir 'tls'
    end
    if not File.directory? 'tls/CA'
      Dir.mkdir 'tls/CA'
    end
    if not File.directory? 'tls/testdriver'
      Dir.mkdir 'tls/testdriver'
    end
    if not File.directory? 'tls/user-cert'
      Dir.mkdir 'tls/user-cert'
    end
    (1..max_rs).each do |i|
      if not File.directory? "tls/rs#{i}"
        Dir.mkdir "tls/rs#{i}"
      end
    end
  end

  def self.createTestServerCert(server_num)
    tmp_cnf = "[SAN]\nsubjectAltName = @altNames\n[altNames]\nDNS.1 = localhost\nDNS.2 = rs#{server_num}.mongodb.test\n"
    tmp_cnf_name = "ext_openssl_rs#{server_num}.cnf"
    File.write(tmp_cnf_name, tmp_cnf)
    system("openssl req -config /etc/ssl/openssl.cnf -new -nodes -keyout tls/rs#{server_num}/rs#{server_num}.key -out tls/rs#{server_num}/rs#{server_num}.csr -days 365 -subj \"/C=US/ST=NY/L=NYC/O=TEST/OU=Server/CN=rs#{server_num}.mongodb.test\"")
    system("openssl x509 -req -days 365 -sha256 -in tls/rs#{server_num}/rs#{server_num}.csr -CA tls/CA/root.crt -CAkey tls/CA/root.key -CAcreateserial -extensions SAN -extfile ext_openssl_rs#{server_num}.cnf -out tls/rs#{server_num}/rs#{server_num}.crt")
    system("cat tls/rs#{server_num}/rs#{server_num}.crt tls/rs#{server_num}/rs#{server_num}.key > tls/rs#{server_num}/rs#{server_num}.pem")
    File.delete(tmp_cnf_name)
  end

  def self.createTestDriverCert
    tmp_cnf = "[SAN]\nsubjectAltName = @altNames\n[altNames]\nDNS.1 = localhost\nDNS.2 = testdriver.mongodb.test\n"
    File.write('ext_openssl_td.cnf', tmp_cnf)
    system('openssl req -config /etc/ssl/openssl.cnf -new -nodes -keyout tls/testdriver/testdriver.key -out tls/testdriver/testdriver.csr -days 365 -subj "/C=US/ST=NY/L=NYC/O=TEST/OU=Server/CN=testdriver.mongodb.test"')
    system('openssl x509 -req -days 365 -sha256 -in tls/testdriver/testdriver.csr -CA tls/CA/root.crt -CAkey tls/CA/root.key -CAcreateserial -out tls/testdriver/testdriver.crt')
    system('cat tls/testdriver/testdriver.key tls/testdriver/testdriver.crt > tls/testdriver/testdriver.pem')
    File.delete('ext_openssl_td.cnf')
  end

  def self.createUserAuthCert
    client_cert_cnf = "basicConstraints = CA:FALSE\nnsCertType = client, email\nkeyUsage = critical,nonRepudiation, digitalSignature, keyEncipherment\nextendedKeyUsage = clientAuth\n"
    File.write('client_auth.cnf', client_cert_cnf)
    system("openssl req -config /etc/ssl/openssl.cnf -new -nodes -keyout tls/user-cert/user-cert.key -out tls/user-cert/user-cert.csr -days 365 -subj \"/C=US/ST=NY/L=NYC/O=TEST/OU=Users/CN=test-user\"")
    #system("openssl x509 -req -days 365 -sha256 -in tls/user-cert/user-cert.csr -CA tls/CA/root.crt -CAkey tls/CA/root.key -CAcreateserial -extensions SAN -extfile ext_openssl_user-test.cnf -out tls/user-test/user-test.crt")
    system("openssl x509 -req -days 365 -sha256 -in tls/user-cert/user-cert.csr -CA tls/CA/root.crt -CAkey tls/CA/root.key -CAcreateserial -extfile client_auth.cnf -out tls/user-cert/user-cert.crt")
    system("cat tls/user-cert/user-cert.key tls/user-cert/user-cert.crt > tls/user-cert/user-cert.pem")
    #File.delete('client_auth.cnf')
  end

  def self.cleanUpCerts(max_rs)
    FileUtils.rm_rf('tls/user-cert/')
    FileUtils.rm_rf('tls/CA/')
    FileUtils.rm_rf('tls/testdriver/')
    (1..max_rs).each do |i|
      FileUtils.rm_rf("tls/rs#{i}/")
    end
    FileUtils.rm_rf('tls/user-cert')
  end


  def self.setupTLSCerts(operation, num_test_servers)
    if operation == 'up' || operation == 'provision'
      if not File.directory?('tls/CA')
        createBaseDirs(num_test_servers)
        # Generate root certificate
        system('openssl req -x509 -config /etc/ssl/openssl.cnf -new -nodes -keyout tls/CA/root.key -sha256 -days 365 -out tls/CA/root.crt -subj "/C=US/ST=NY/L=NYC/O=TEST/CN=auth.mongodb.test"')
        # Generate certificates for the replica set members
        (1..num_test_servers).each do |i|
          createTestServerCert(i)
        end
        createTestDriverCert
        createUserAuthCert
      end
    elsif operation == 'destroy'
      cleanUpCerts num_test_servers
    end
  end

  def self.setupSSHKeys(operation)
    insecure_ssh_pub_key = ''
    if operation == 'up' || operation == 'provision'
      if not File.directory?("ssh")
        Dir.mkdir('ssh')
        system('ssh-keygen -t ed25519 -f ssh/id_ed25519 -N ""')
        insecure_ssh_pub_key = File.readlines('ssh/id_ed25519.pub').first.strip
      end
    elsif operation == 'destroy'
      FileUtils.rm_rf('ssh')
    end
    return insecure_ssh_pub_key
  end

end
