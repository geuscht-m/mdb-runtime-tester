var conf = rs.conf();
conf.settings.heartbeatTimeoutSecs = 1;
conf.settings.electionTimeoutMillis = 2000;
rs.reconfig(conf);
