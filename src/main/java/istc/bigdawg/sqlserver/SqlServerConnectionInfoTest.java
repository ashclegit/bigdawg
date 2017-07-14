/**
 *
 */
package istc.bigdawg.sqlserver;

import istc.bigdawg.properties.BigDawgConfigProperties;

/**
 * @author Kate Yu
 */
public class SqlServerConnectionInfoTest extends SqlServerConnectionInfo {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    /**
     * @param host
     * @param port
     * @param database
     * @param user
     * @param password
     */
    public SqlServerConnectionInfoTest() {
        super(BigDawgConfigProperties.INSTANCE.getSqlServerTestHost(),
                BigDawgConfigProperties.INSTANCE.getSqlServerTestPort(),
                BigDawgConfigProperties.INSTANCE.getSqlServerTestDatabase(),
                BigDawgConfigProperties.INSTANCE.getSqlServerTestUser(),
                BigDawgConfigProperties.INSTANCE.getSqlServerTestPassword());
    }

}