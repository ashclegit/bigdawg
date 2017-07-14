package istc.bigdawg.migration.datatypes;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.create.table.CreateTable;

import javax.annotation.Nullable;
import net.sf.jsqlparser.statement.Statement;

import java.util.HashMap;
import java.util.Map;

/**
 * Class to translate between SQLServer and Postgres (and other engines)
 * Created by akmr on 07/01/2017.
 */
public class SQLServerPostgresTranslation {
    // bidirectional map mapping SQLServer datatypes to their PostgreSQL equivalents for straightforward replacements
    private static Map<String, String> sqlserverToPostgres;
    private static Map<String, String> postgresToSqlServer;
    private static int MAP_SIZE = 16;

    // Used https://github.com/ChrisLundquist/pg2mysql for reference
    static {
        sqlserverToPostgres = new HashMap<>();
        sqlserverToPostgres.put("datetime", "timestamp");
        sqlserverToPostgres.put("int", "integer");
        sqlserverToPostgres.put("bit", "boolean");
        sqlserverToPostgres.put("binary", "bytea");
        sqlserverToPostgres.put("int UNSIGNED", "int_unsigned");
        sqlserverToPostgres.put("smallint UNSIGNED", "smallint_unsigned");
        sqlserverToPostgres.put("bigint UNSIGNED", "bigint_unsigned");
        sqlserverToPostgres.put("int identity", "serial");
        sqlserverToPostgres.put("double precision", "double precision");
        sqlserverToPostgres.put("real", "real");
        sqlserverToPostgres.put("bit DEFAULT 1", "bool DEFAULT true");
        sqlserverToPostgres.put("bit DEFAULT 0", "bool DEFAULT false");
        sqlserverToPostgres.put("varchar", "character varying");

        postgresToSqlServer = new HashMap<>();
        for (String k : sqlserverToPostgres.keySet()) {
            postgresToSqlServer.put(sqlserverToPostgres.get(k), k);
        }
        postgresToSqlServer.put(" without time zone", "");
        postgresToSqlServer.put("now()", "CURRENT_TIMESTAMP");
    }
    /**
     * Replace SQLServer types with Postgres-compatible types.
     */
    public static String convertToPostgres(String statementStr) {
        try {
            String pgStatementStr = statementStr.replace("`", "");
            pgStatementStr = pgStatementStr.replace("DEFAULT NULL", "");
            for (String key: sqlserverToPostgres.keySet()) {
                pgStatementStr = pgStatementStr.replace(key, sqlserverToPostgres.get(key));
            }
            Statement statement = CCJSqlParserUtil.parse(pgStatementStr);
            if (statement instanceof CreateTable) {
                CreateTable ct = (CreateTable) statement;
                // clear table options list
                ct.setTableOptionsStrings(null);
            }
            return statement.toString();
        } catch (JSQLParserException e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * Replace Postgres types to SQLServer-compatible types.
     */
    public static String convertToSqlServer(String statementStr) {
            String msStatementStr = statementStr;
            for (String key: postgresToSqlServer.keySet()) {
                msStatementStr = msStatementStr.replace(key, postgresToSqlServer.get(key));
            }
            return msStatementStr;
    }

    public static void main(String[] args) {
//        String statement = "CREATE TABLE d_patients (\n" +
//                "  subject_id int(11) NOT NULL,\n" +
//                "  sex varchar(1) DEFAULT NULL,\n" +
//                "  dob datetime NOT NULL,\n" +
//                "  dod datetime DEFAULT NULL,\n" +
//                "  hospital_expire_flg varchar(1) DEFAULT 'N'\n" +
//                ") ENGINE=InnoDB DEFAULT CHARSET=latin1";
//        System.out.println(convertToPostgres(statement));
          String statement = "CREATE TABLE IF NOT EXISTS " +
                  "patients2 (subject_id integer, sex character" +
                  " varying(1), dob timestamp without time zone," +
                  " dod timestamp without time zone, hospital_expire_flg" +
                  " character varying(1))";
          System.out.println(convertToSqlServer(statement));
    }
}