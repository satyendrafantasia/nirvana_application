package com.nirvana.application.config;

/** MariaDB exposes its JSON alias as LONGTEXT in JDBC schema metadata. */
public class LocalMariaDbDialect extends org.hibernate.dialect.MariaDBDialect {
    @Override
    public boolean equivalentTypes(int first, int second) {
        if ((first == org.hibernate.type.SqlTypes.JSON && second == java.sql.Types.LONGVARCHAR)
                || (second == org.hibernate.type.SqlTypes.JSON && first == java.sql.Types.LONGVARCHAR)) return true;
        return super.equivalentTypes(first, second);
    }
}
