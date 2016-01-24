/**
 * 
 */
package istc.bigdawg.migration;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;

import istc.bigdawg.LoggerSetup;
import istc.bigdawg.exceptions.MigrationException;
import istc.bigdawg.exceptions.RunShellException;
import istc.bigdawg.exceptions.SciDBException;
import istc.bigdawg.postgresql.PostgreSQLColumnMetaData;
import istc.bigdawg.postgresql.PostgreSQLConnectionInfo;
import istc.bigdawg.postgresql.PostgreSQLHandler;
import istc.bigdawg.query.ConnectionInfo;
import istc.bigdawg.scidb.SciDBConnectionInfo;
import istc.bigdawg.scidb.SciDBHandler;
import istc.bigdawg.util.SystemUtilities;
import istc.bigdawg.utils.Constants;
import istc.bigdawg.utils.RunShell;

/**
 * @author Adam Dziedzic
 *
 */
public class FromPostgresToSciDB implements FromDatabaseToDatabase {

	private static Logger log = Logger.getLogger(FromPostgresToSciDB.class);

	private String getCopyCommandPostgreSQL(String table, String delimiter) {
		StringBuilder copyFromStringBuf = new StringBuilder();
		copyFromStringBuf.append("COPY ");
		copyFromStringBuf.append(table + " ");
		copyFromStringBuf.append("TO ");
		copyFromStringBuf.append(" STDOUT ");
		copyFromStringBuf.append("with (format csv, delimiter '"+delimiter+"')");
		return copyFromStringBuf.toString();
	}

	/**
	 * 
	 */
	public MigrationResult migrateSingleThreadCSV(PostgreSQLConnectionInfo connectionFrom, String fromTable,
			SciDBConnectionInfo connectionTo, String arrayTo) {
		String generalMessage = "Data migration from Postgres to SciDB";
		log.info(generalMessage);
		String errMessage = generalMessage + " failed! ";
		String delimiter = "|";
		String copyCommandPostgreSQL = getCopyCommandPostgreSQL(fromTable, delimiter);
		Connection conPostgreSQL;
		try {
			conPostgreSQL = PostgreSQLHandler.getConnection(connectionFrom);
		} catch (SQLException e) {
			e.printStackTrace();
			return MigrationResult.getFailedInstance(errMessage + "Could not connect to PostgreSQL!" + e.getMessage());
		}
		CopyManager copyManagerPostgreSQL;
		try {
			copyManagerPostgreSQL = new CopyManager((BaseConnection) conPostgreSQL);
		} catch (SQLException e1) {
			e1.printStackTrace();
			return MigrationResult
					.getFailedInstance(errMessage + "PostgreSQL Copy Manager creation failed. " + e1.getMessage());
		}
		String csvFilePath = SystemUtilities.getSystemTempDir() + "/bigdawg_" + fromTable + ".csv";
		String scidbFilePath = SystemUtilities.getSystemTempDir() + "/bigdawg_" + fromTable + ".scidb";
		FileWriter writer = null;
		try {
			try {
				writer = new FileWriter(csvFilePath);
			} catch (IOException e) {
				e.printStackTrace();
				return MigrationResult.getFailedInstance(
						errMessage + " Problem with opening a file: " + csvFilePath + " for writing.");
			}
			Long extractedRows;
			try {
				extractedRows = copyManagerPostgreSQL.copyOut(copyCommandPostgreSQL, writer);
				log.debug(generalMessage + " extracted rows from PostgreSQL: " + extractedRows);
			} catch (SQLException | IOException e) {
				e.printStackTrace();
				return MigrationResult.getFailedInstance(errMessage
						+ " PostgreSQL Copy Manager: extracting data from PostgreSQL failed. " + e.getMessage());
			}
			try {
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
				return MigrationResult.getFailedInstance(
						errMessage + " Problem with closing the file: " + csvFilePath + " " + e.getMessage());
			}
			/**
			 * We need to know the attribute types in PostgreSQL for csv2scidb
			 * conversion.
			 */
			List<PostgreSQLColumnMetaData> columnsMetaData;
			try {
				columnsMetaData = new PostgreSQLHandler(connectionFrom).getColumnsMetaData(fromTable);
			} catch (SQLException e) {
				e.printStackTrace();
				return MigrationResult.getFailedInstance(
						errMessage + " Extraction of the attribute types from PostgreSQL failed. " + e.getMessage());
			}
			String typesPattern = SciDBHandler.getTypePatternFromPostgresTypes(columnsMetaData);

			ProcessBuilder csv2scidb = new ProcessBuilder(connectionTo.getBinPath() + "csv2scidb", "-i", csvFilePath,
					"-o", scidbFilePath, "-d", delimiter, "-p", typesPattern);
			log.debug(csv2scidb.command());
			try {
				RunShell.runShell(csv2scidb);
			} catch (RunShellException | InterruptedException | IOException e) {
				e.printStackTrace();
				return MigrationResult.getFailedInstance(
						errMessage + " Conversion from csv to scidb format failed! " + e.getMessage());
			}
			// save the disk space
			SystemUtilities.deleteFileIfExists(csvFilePath);
			try {
				loadDataToSciDB(connectionTo, arrayTo, scidbFilePath);
			} catch (IOException | InterruptedException | SciDBException e) {
				return MigrationResult.getFailedInstance(
						errMessage + " Final data loading to SciDB failed! " + e.getMessage());
			}
			return new MigrationResult(extractedRows, null, "No information about loaded rows.", false);
		} finally {
			SystemUtilities.deleteFileIfExists(csvFilePath);
			SystemUtilities.deleteFileIfExists(scidbFilePath);
		}
	}

	public String loadDataToSciDB(SciDBConnectionInfo conTo, String arrayTo, String dataFile)
			throws IOException, InterruptedException, SciDBException {
		InputStream resultInStream = RunShell.executeAQLcommandSciDB(conTo.getHost(), conTo.getPort(),
				conTo.getBinPath(), "load " + arrayTo + " from '" + dataFile + "'");
		String resultString = IOUtils.toString(resultInStream, Constants.ENCODING);
		log.debug("Load data to SciDB: " + resultString);
		return resultString;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * istc.bigdawg.migration.FromDatabaseToDatabase#migrate(istc.bigdawg.query.
	 * ConnectionInfo, java.lang.String, istc.bigdawg.query.ConnectionInfo,
	 * java.lang.String)
	 */
	@Override
	public MigrationResult migrate(ConnectionInfo connectionFrom, String objectFrom, ConnectionInfo connectionTo,
			String objectTo) throws MigrationException {
		log.debug("General data migration: "+this.getClass().getName());
		if (connectionFrom instanceof PostgreSQLConnectionInfo && connectionTo instanceof SciDBConnectionInfo) {
			try {
				return this.migrateSingleThreadCSV((PostgreSQLConnectionInfo) connectionFrom, objectFrom,
						(SciDBConnectionInfo) connectionTo, objectTo);
			} catch (Exception e) {
				throw new MigrationException(e.getMessage(), e);
			}
		}
		return null;
	}

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		LoggerSetup.setLogging();
		FromPostgresToSciDB migrator = new FromPostgresToSciDB();
		PostgreSQLConnectionInfo conFrom = new PostgreSQLConnectionInfo("localhost", "5431", "tpch", "postgres",
				"test");
		String fromTable = "region";
		SciDBConnectionInfo conTo = new SciDBConnectionInfo("localhost", "1239", "scidb", "mypassw",
				"/opt/scidb/14.12/bin/");
		String arrayTo = "region";
		migrator.migrateSingleThreadCSV(conFrom, fromTable, conTo, arrayTo);
	}

}

/*
0    [main] INFO  istc.bigdawg.LoggerSetup  - Starting application. Logging was configured!
94   [main] INFO  istc.bigdawg.migration.FromPostgresToSciDB  - Data migration from Postgres to SciDB
110  [main] DEBUG istc.bigdawg.migration.FromPostgresToSciDB  - Data migration from Postgres to SciDB extracted rows from PostgreSQL: 5
121  [main] DEBUG istc.bigdawg.postgresql.PostgreSQLHandler  - replace double quotes (") with signle quotes in the query to run it in PostgreSQL: SELECT column_name, ordinal_position, is_nullable, data_type, character_maximum_length, numeric_precision, numeric_scale FROM information_schema.columns WHERE table_schema="public" and table_name="region" order by ordinal_position
156  [main] DEBUG istc.bigdawg.migration.FromPostgresToSciDB  - [/opt/scidb/14.12/bin/csv2scidb, -i, /tmp/bigdawg_region.csv, -o, /tmp/bigdawg_region.scidb, -d, |, -p, NSS]
548  [main] INFO  istc.bigdawg.utils.RunShell  - command to be executed in SciDB: load region from /tmp/bigdawg_region.scidb; on host: localhost port: 1239 SciDB bin path: /opt/scidb/14.12/bin/
673  [main] DEBUG istc.bigdawg.migration.FromPostgresToSciDB  - Load data to SciDB: Query was executed successfully
*/
