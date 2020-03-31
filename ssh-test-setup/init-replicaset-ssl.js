rs.initiate( {
  _id: "replTestTLS",
  members: [
    { _id: 0, host: "rs1.mongodb.test:28017" },
    { _id: 1, host: "rs2.mongodb.test:28017" },
    { _id: 2, host: "rs3.mongodb.test:28017" }
  ]
})
