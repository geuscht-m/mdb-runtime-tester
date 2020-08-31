rs.initiate( {
  _id: "replTest",
  members: [
    { _id: 0, host: "rs1.mongodb.test:27017" },
    { _id: 1, host: "rs2.mongodb.test:27017" },
    { _id: 2, host: "rs3.mongodb.test:27017" }
  ]
})
