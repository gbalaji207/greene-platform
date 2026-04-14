package com.greene.core.auth.domain

import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.usertype.UserType
import org.postgresql.util.PGobject
import java.io.Serializable
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

/**
 * Hibernate [UserType] for PostgreSQL's `inet` column type.
 *
 * ## Why `AttributeConverter` doesn't work here
 * `AttributeConverter<String, PGobject>` hands a `PGobject` back to Hibernate, which then
 * decides how to bind it.  Because `PGobject` is not in Hibernate 6's basic-type registry
 * (it only implements `Serializable`), Hibernate falls back to binary serialisation → `bytea`,
 * which PostgreSQL refuses to cast to `inet`.
 *
 * ## Why `UserType` works
 * `nullSafeSet` has direct access to the [PreparedStatement].  Calling
 * `st.setObject(index, PGobject(type="inet", value=…), Types.OTHER)` passes the `PGobject`
 * straight to the PostgreSQL JDBC driver, which resolves the OID from `PGobject.type`
 * and binds the value correctly — no implicit cast needed.
 */
class InetUserType : UserType<String> {

    override fun getSqlType(): Int = Types.OTHER

    override fun returnedClass(): Class<String> = String::class.java

    override fun equals(x: String?, y: String?): Boolean = x == y

    override fun hashCode(x: String?): Int = x?.hashCode() ?: 0

    override fun nullSafeGet(
        rs: ResultSet, position: Int,
        session: SharedSessionContractImplementor, owner: Any?,
    ): String? = (rs.getObject(position) as? PGobject)?.value

    override fun nullSafeSet(
        st: PreparedStatement, value: String?, index: Int,
        session: SharedSessionContractImplementor,
    ) {
        if (value == null) {
            st.setNull(index, Types.OTHER)
        } else {
            st.setObject(
                index,
                PGobject().apply { type = "inet"; this.value = value },
                Types.OTHER,
            )
        }
    }

    override fun deepCopy(value: String?): String? = value

    override fun isMutable(): Boolean = false

    override fun disassemble(value: String?): Serializable? = value

    override fun assemble(cached: Serializable?, owner: Any?): String? = cached as? String
}

