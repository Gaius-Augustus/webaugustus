dataSource {
    pooled = true
	dbCreate = "update"
    driverClassName = "com.mysql.jdbc.Driver"
    dialect = org.hibernate.dialect.MySQL5InnoDBDialect
    username = "xxx"
    password = "xxx"
//    logSql = true
    properties {
        maxIdle = 25
        minIdle = 5
        initialSize = 5
        minEvictableIdleTimeMillis = 60000
        timeBetweenEvictionRunsMillis = 60000
        maxWait = 10000
        maxAge = 10 * 60000
        validationQuery = "SELECT 1"
        validationQueryTimeout = 3
        validationInterval = 15000
        testOnBorrow = true
        testWhileIdle = true
    }                       
}
hibernate {
    cache.use_second_level_cache = true
    cache.use_query_cache = false
//    cache.region.factory_class = 'org.hibernate.cache.SingletonEhCacheRegionFactory' // Hibernate 3
    cache.region.factory_class = 'org.hibernate.cache.ehcache.SingletonEhCacheRegionFactory' // Hibernate 4
    singleSession = true // configure OSIV singleSession mode
    flush.mode = 'auto' // OSIV session flush mode outside of transactional context
}

// environment specific settings
environments {
    development {
        dataSource {
            url = "jdbc:mysql://localhost/webaugustus_devDB"
        }
    }
    test {
        dataSource {
            url = "jdbc:mysql://localhost/webaugustus_devDB"
        }
    }
    production {
        dataSource {
            url = "jdbc:mysql://localhost/webaugustusDB"
        }
    }
}
