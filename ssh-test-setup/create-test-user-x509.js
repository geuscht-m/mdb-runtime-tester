db.getSiblingDB("$external").runCommand({ createUser: "CN=test-user,OU=Users,O=TEST,L=NYC,ST=NY,C=US", roles: [ { role: "root", db: "admin" } ] });

