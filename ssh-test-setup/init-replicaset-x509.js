rs.initiate( {
  _id: "replTestX509",
  members: [
    { _id: 0, host: "rs1.mongodb.test:29017" },
    { _id: 1, host: "rs2.mongodb.test:29017" },
    { _id: 2, host: "rs3.mongodb.test:29017" }
  ]
});
sleep(1000);
//db.getSiblingDB("$external").runCommand({ createUser: "CN=test-user,OU=Users,O=TEST,L=NYC,ST=NY,C=US", roles: [ { role: "root", db: "admin" }  ] });
