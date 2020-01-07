#!/bin/sh
mkdir -p /home/vagrant/bin
wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein -O /home/vagrant/bin/lein
chown vagrant:vagrant /home/vagrant/bin/lein
chmod a+x /home/vagrant/bin/lein
