
package istc.bigdawg.sqlserver;

import istc.bigdawg.BDConstants.Shim;
import istc.bigdawg.catalog.CatalogViewer;
import istc.bigdawg.database.AttributeMetaData;
import istc.bigdawg.executor.IslandQueryResult;
import istc.bigdawg.executor.JdbcQueryResult;
import istc.bigdawg.executor.QueryResult;
import istc.bigdawg.query.QueryClient;
import istc.bigdawg.relational.RelationalHandler;
import istc.bigdawg.relational.RelationalSchemaTableName;
import istc.bigdawg.relational.RelationalTableMetaData;
import istc.bigdawg.query.ConnectionInfo;
import istc.bigdawg.utils.LogUtils;
import istc.bigdawg.utils.StackTrace;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import javax.ws.rs.core.Response;
import java.sql.*;
import java.util.*;

/**
 * @author akmr
 * 
 */
public class SQLServerHandler implements RelationalHandler {

	/**
	 * log
	 */
	private static Logger log = Logger
			.getLogger(SQLServerHandler.class.getName());

	/**
	 * Information about connection to SQLServer (e.g. IP, port, etc.).
	 */
	private SqlServerConnectionInfo conInfo = null;

	private Connection con = null;
	private Statement st = null;
	private PreparedStatement preparedSt = null;
	private ResultSet rs = null;

	/**
	 * Initialize the SQLServer handler with only the connection information.
	 *
	 * @param conInfo information about connection to an instance of SQLServer
	 *                database
	 */
	public SQLServerHandler(SqlServerConnectionInfo conInfo) {
		this.conInfo = conInfo;
	}

	/**
	 * Initialize the instance with connection information and physical
	 * connection.
	 *
	 * @param conInfo    Information aobut the connection to SQLServer (host,
	 *                   password, user, etc.).
	 * @param connection Physical connection to SQLServer.
	 */
	public SQLServerHandler(ConnectionInfo conInfo, Connection connection) {
		this(conInfo);
		if (connection == null) {
			throw new IllegalArgumentException(
					"Physical connection to SQLServer has to be not null.");
		}
		this.con = connection;
	}

	public SQLServerHandler(ConnectionInfo conInfo) {
		if (conInfo instanceof SqlServerConnectionInfo) {
			this.conInfo = (SqlServerConnectionInfo) conInfo;
		} else {
			Exception e = new IllegalArgumentException(
					"The conInfo parameter has to be of "
							+ "type: SqlServerConnectionInfo.");
			log.error(e.getMessage() + " " + StackTrace.getFullStackTrace(e));
		}
	}

	public SQLServerHandler(int dbId) throws Exception {
		try {
			this.conInfo = (SqlServerConnectionInfo) CatalogViewer
					.getConnectionInfo(dbId);
		} catch (Exception e) {
			String msg = "Catalog chosen connection: " + conInfo.getHost() + " "
					+ conInfo.getPort() + " " + conInfo.getDatabase() + " "
					+ conInfo.getUser() + " " + conInfo.getPassword() + ".";
			log.error(msg);
			e.printStackTrace();
			throw e;
		}
	}

	public SQLServerHandler() {
		String msg = "Default handler. SQLServer parameters are "
				+ "taken from the BigDAWG configuration file.";
		log.info(msg);
	}

	/**
	 * Establish connection to SQLServer for this instance.
	 *
	 * @throws SQLException if could not establish a connection
	 */
	public static Connection getConnection(ConnectionInfo ci) throws SQLException {
		return RelationalHandler.getConnection(ci);
	}

	/**
	 * Establish connection to SQLServer for this instance.
	 *
	 * @throws SQLException if could not establish a connection
	 */
	@Override
	public Connection getConnection() throws SQLException {
		if (con == null) {
			if (conInfo != null) {
				try {
					con = RelationalHandler.getConnection(conInfo);
				} catch (SQLException e) {
					e.printStackTrace();
					log.error(e.getMessage()
							+ " Could not connect to SQLServer database using: "
							+ conInfo.toString(), e);
					throw e;
				}
			} else {
				throw new IllegalStateException("Connection should not be null");
			}
		}
		return con;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see istc.bigdawg.query.DBHandler#executeQuery(java.lang.String)
	 */
	@Override
	public Response executeQuery(String queryString) {
		long lStartTime = System.nanoTime();
		JdbcQueryResult queryResult = null;
		try {
			queryResult = executeQueryOnEngine(queryString);
		} catch (SQLException e) {
			return Response.status(500)
					.entity("Problem with query execution in SQLServer: "
							+ e.getMessage() + "; query: " + queryString)
					.build();
			// return "Problem with query execution in PostgreSQL: " +
			// queryString;
		}
		String messageQuery = "SQLServer query execution time milliseconds: "
				+ (System.nanoTime() - lStartTime) / 1000000 + ",";
		log.info(messageQuery);

		lStartTime = System.nanoTime();

		String out = "";
		for (String name : queryResult.getColNames()) {
			out = out + "\t" + name;
		}
		out = out + "\n";
		Integer rowCounter = 1;
		for (List<String> row : queryResult.getRows()) {
			out = out + rowCounter.toString() + ".";
			for (String s : row) {
				out = out + "\t" + s;
			}
			out = out + "\n";
			rowCounter += 1;
		}

		String messageTABLE = "format TABLE Java time milliseconds: "
				+ (System.nanoTime() - lStartTime) / 1000000 + ",";
		log.info(messageTABLE);

		return Response.status(200).entity(out).build();
	}

	/**
	 * Execute an SQL statement on a given connection.
	 *
	 * @param connection
	 *            connection on which the statement is executed
	 * @param stringStatement
	 *            sql statement to be executed
	 * @throws SQLException
	 */
	public static void executeStatement(Connection connection,
										String stringStatement) throws SQLException {
		Statement statement = null;
		try {
			statement = connection.createStatement();
			log.debug(
					"Statement to be executed in SQLServer: " + stringStatement);
			statement.execute(stringStatement);
			statement.close();
		} catch (SQLException ex) {
			ex.printStackTrace();
			log.error(ex.getMessage() + "; statement to be executed: "
					+ LogUtils.replace(stringStatement) + " "
					+ ex.getStackTrace(), ex);
			throw ex;
		} finally {
			if (statement != null) {
				statement.close();
			}
		}
	}

	/**
	 * Clean resource after a query/statement was executed in SQLServer.
	 *
	 * @throws SQLException
	 */
	private void cleanSQLServerResources() throws SQLException {
		if (rs != null) {
			rs.close();
			rs = null;
		}
		if (st != null) {
			st.close();
			st = null;
		}
		if (preparedSt != null) {
			preparedSt.close();
			preparedSt = null;
		}
		if (con != null) {
			con.close();
			con = null;
		}
	}

	/**
	 * Executes a statement (not a query) in SQLServer. It cleans the resources
	 * at the end.
	 *
	 * @param statement to be executed
	 * @throws SQLException
	 */
	public void executeStatementOnConnection(String statement)
			throws SQLException {
		getConnection();
		try {
			RelationalHandler.executeStatement(con, statement);
		} finally {
			try {
				this.cleanSQLServerResources();
			} catch (SQLException ex) {
				ex.printStackTrace();
				log.info(ex.getMessage() + "; statement: "
						+ LogUtils.replace(statement), ex);
				throw ex;
			}
		}
	}

	/**
	 * It executes the SQL command and releases the resources at the end,
	 * returning a QueryResult if present
	 *
	 * @param query
	 * @return #Optional<QueryResult>
	 * @throws SQLException
	 */
	public Optional<QueryResult> execute(final String query)
			throws LocalQueryExecutionException {
		try {

			log.debug("SQLServerHandler is attempting query: " + LogUtils.replace(query) + "");
			log.debug("ConnectionInfo:\n" + this.conInfo.toString());

			this.getConnection();
			st = con.createStatement();
			if (st.execute(query)) {
				rs = st.getResultSet();
				return Optional.of(new JdbcQueryResult(rs, this.conInfo));
			} else {
				return Optional.of(new IslandQueryResult(this.conInfo));
			}
		} catch (SQLException ex) {
			Logger lgr = Logger.getLogger(QueryClient.class.getName());
			// ex.printStackTrace();
			lgr.log(Level.ERROR,
					ex.getMessage() + "; query: " + LogUtils.replace(query),
					ex);
			throw new LocalQueryExecutionException(ex);
		} finally {
			try {
				this.cleanSQLServerResources();
			} catch (SQLException ex) {
				Logger lgr = Logger.getLogger(QueryClient.class.getName());
				// ex.printStackTrace();
				lgr.log(Level.INFO,
						ex.getMessage() + "; query: " + LogUtils.replace(query),
						ex);
				throw new LocalQueryExecutionException(ex);
			}
		}
	}

	@Override
	public void dropDataSetIfExists(String dataSetName) throws Exception {

	}

	/**
	 * It executes the query and releases the resources at the end.
	 *
	 * @param query
	 * @return #JdbcQueryResult
	 * @throws SQLException
	 */
	public JdbcQueryResult executeQueryOnEngine(final String query)
			throws SQLException {
		try {
			this.getConnection();

			log.debug("\n\nquery: " + LogUtils.replace(query) + "");
			if (this.conInfo != null) {
				log.debug("ConnectionInfo: " + this.conInfo.toString() + "\n");
			}

			st = con.createStatement();
			rs = st.executeQuery(query);

			return new JdbcQueryResult(rs, this.conInfo);
		} catch (SQLException ex) {
			Logger lgr = Logger.getLogger(QueryClient.class.getName());
			// ex.printStackTrace();
			lgr.log(Level.ERROR,
					ex.getMessage() + "; query: " + LogUtils.replace(query),
					ex);
			throw ex;
		} finally {
			try {
				this.cleanSQLServerResources();
			} catch (SQLException ex) {
				Logger lgr = Logger.getLogger(QueryClient.class.getName());
				// ex.printStackTrace();
				lgr.log(Level.INFO,
						ex.getMessage() + "; query: " + LogUtils.replace(query),
						ex);
				throw ex;
			}
		}
	}

	@Override
	public Shim getShim() {
		return Shim.PSQLRELATION;
	}

	/**
	 * NEW FUNCTION generate the "CREATE TABLE" clause from existing tables on
	 * DB. Recommend use with 'bigdawg_schema'
	 *
	 * @param schemaAndTableName
	 * @return
	 * @throws SQLException
	 */
	@Override
	public String getCreateTable(String schemaAndTableName)
			throws SQLException {
		try {
			getConnection();
			return getCreateTable(con, schemaAndTableName, schemaAndTableName);
		} finally {
			try {
				this.cleanSQLServerResources();
			} catch (SQLException ex) {
				ex.printStackTrace();
				log.error(ex.getMessage() + "; conInfo: " + conInfo.toString()
						+ "; schemaAndTableName: " + schemaAndTableName + " "
						+ ex.getStackTrace(), ex);
				throw ex;
			}
		}
	}
	
	/**
	 * Get metadata about columns (column name, position, data type, etc) for a
	 * table in SQLServer.
	 *
	 * @param tableNameInitial the name of the table
	 * @return map column name to column meta data
	 * @throws SQLException if the data extraction from PostgreSQL failed
	 */
	@Override
	public RelationalTableMetaData getObjectMetaData(String tableNameInitial)
			throws SQLException {
		try {
			this.getConnection();
			SQLServerSchemaTableName schemaTable = new SQLServerSchemaTableName(
					tableNameInitial);
			try {
				preparedSt = con.prepareStatement(
						"SELECT column_name, ordinal_position, is_nullable, "
								+ "data_type, character_maximum_length, "
								+ "numeric_precision, numeric_scale "
								+ "FROM information_schema.columns "
								+ "WHERE table_schema=? and table_name ilike ?"
								+ " order by ordinal_position;");
				preparedSt.setString(1, schemaTable.getSchemaName());
				preparedSt.setString(2, schemaTable.getTableName());
				// postgresql logger cannot accept single quotes
				log.debug("replace double quotes (\") with signle quotes "
						+ "in the query to log it in SQLServer: "
						+ preparedSt.toString().replace("'", "\""));
			} catch (SQLException e) {
				e.printStackTrace();
				log.error("SQLServerHandler, the query preparation failed. "
						+ e.getMessage() + " "
						+ StackTrace.getFullStackTrace(e));
				throw e;
			}
			ResultSet resultSet = preparedSt.executeQuery();
			Map<String, AttributeMetaData> columnsMap = new HashMap<>();
			List<AttributeMetaData> columnsOrdered = new ArrayList<>();
			/*
			 * resultSet.isBeforeFirst() returns false if the cursor is not
			 * before the first record or if there are no rows in the result set
			 */
			if (!resultSet.isBeforeFirst()) {
				throw new IllegalArgumentException(String.format(
						"No results were found for the table: %s; connection: %s",
						schemaTable.getFullName(), this.conInfo));
			}
			while (resultSet.next()) {
				/**
				 * ordinal position in SQLServer starts from 1 but we want it
				 * to start from 0.
				 */
				AttributeMetaData columnMetaData = new AttributeMetaData(
						resultSet.getString(1), resultSet.getInt(2) - 1,
						resultSet.getBoolean(3), resultSet.getString(4),
						resultSet.getInt(5), resultSet.getInt(6),
						resultSet.getInt(7));
				columnsMap.put(resultSet.getString(1), columnMetaData);
				columnsOrdered.add(columnMetaData);
			}
			return new SQLServerTableMetaData(schemaTable, columnsMap,
					columnsOrdered);
		} finally {
			try {
				this.cleanSQLServerResources();
			} catch (SQLException ex) {
				ex.printStackTrace();
				log.error(ex.getMessage() + "; conInfo: " + conInfo.toString()
						+ "; table: " + tableNameInitial + " "
						+ StackTrace.getFullStackTrace(ex), ex);
				throw ex;
			}
		}
	}

	/**
	 * Get names of the column in the table.
	 *
	 * @param table the name of the table
	 * @return list of names of columns for the table
	 * @throws SQLException
	 */
	public List<String> getColumnNames(String table) throws SQLException {
		List<String> columnNames = new ArrayList<>();
		for (AttributeMetaData meta : getObjectMetaData(table)
				.getAttributesOrdered()) {
			columnNames.add(meta.getName());
		}
		return columnNames;
	}

	/**
	 * Check if a schema exists.
	 *
	 * @param schemaName the name of the schema to be checked if exists
	 * @return
	 * @throws SQLException
	 */
	public boolean existsSchema(String schemaName) throws SQLException {
		try {
			this.getConnection();
			try {
				preparedSt = con.prepareStatement(
						"select exists (select 1 from information_schema.schemata where schema_name=?)");
				preparedSt.setString(1, schemaName);
			} catch (SQLException e) {
				e.printStackTrace();
				log.error(e.getMessage()
						+ " SQLServerHandler, the query preparation for checking if a schema exists failed.");
				throw e;
			}
			try {
				ResultSet rs = preparedSt.executeQuery();
				rs.next();
				return rs.getBoolean(1);
			} catch (SQLException e) {
				e.printStackTrace();
				log.error(e.getMessage()
						+ " Failed to check if a schema exists.");
				throw e;
			}
		} finally {
			try {
				this.cleanSQLServerResources();
			} catch (SQLException ex) {
				ex.printStackTrace();
				log.error(ex.getMessage() + "; conInfo: " + conInfo.toString()
						+ "; schemaName: " + schemaName, ex);
				throw ex;
			}
		}
	}

	@Override
	public void createSchemaIfNotExists(String schemaName) throws SQLException {
		executeStatementOnConnection("create schema if not exists " + schemaName);
	}

	@Override
	public void createTable(String createTableStatement) throws SQLException {
		executeStatementOnConnection(createTableStatement);
	}

	public void dropSchemaIfExists(String schemaName) throws SQLException {
		executeStatementOnConnection("drop schema if exists " + schemaName);
	}

	public void dropTableIfExists(String tableName) throws SQLException {
		executeStatementOnConnection("drop table if exists " + tableName);
	}

	/**
	 * Check if the table with a given name exists in the SQLServer database.
	 *
	 * @see istc.bigdawg.query.DBHandler#existsObject(java.lang.String)
	 */
	@Override
	public boolean existsObject(String name) throws SQLException {
		return existsTable(new SQLServerSchemaTableName(name));
	}

	public static String getCreateTable(Connection conFrom, String fromTable, String targetTableName)
			throws SQLException {
		try {
			StringBuilder extraction = new StringBuilder("CREATE TABLE IF NOT EXISTS " + targetTableName);

			Statement st = conFrom.createStatement();

			ResultSet rs = st.executeQuery("SHOW CREATE TABLE " + fromTable + ";");

			if (rs.next()) {
				String command = rs.getString(2);
				// Strip out the beginning
				String rest = command.substring(command.indexOf("("));
				extraction.append(rest);
			}
			rs.close();
			st.close();
			return extraction.toString();
		} catch (SQLException ex) {
			ex.printStackTrace();
			log.error(ex.getMessage() + "; conInfo: " + conFrom.getClientInfo()
					+ "; fromTable: " + fromTable + " ; toTable: "
					+ targetTableName + StackTrace.getFullStackTrace(ex), ex);
			throw ex;
		}
	}

	/**
	 * Check if a given table exists in the SQLServer database.
	 *
	 * @param connectionInfo The information about the connection to PostgreSQL.
	 * @param table          The name of the table in PostgreSQL.
	 * @return true if table exists, false otherwise.
	 * @throws SQLException Problem with a connection to PostgreSQL.
	 */
	public static boolean existsTable(ConnectionInfo connectionInfo,
									  String table) throws SQLException {
		SQLServerHandler handler = new SQLServerHandler(connectionInfo);
		boolean existsTable = handler
				.existsTable(new SQLServerSchemaTableName(table));
		try {
			handler.close();
		} catch (Exception ex) {
			log.error("Could not close SQLServerHandler. " + ex.getMessage());
		}
		return existsTable;
	}

	/**
	 * @param table The name of the table.
	 * @return true if the table exists, false if there is no such table
	 * @throws SQLException
	 * @see #existsTable(SQLServerSchemaTableName)
	 */
	public boolean existsTable(String table) throws SQLException {
		return existsTable(new SQLServerSchemaTableName(table));
	}

	/**
	 * Check if a table exists.
	 *
	 * @param schemaTable names of a schema and a table
	 * @return true if the table exists, false if there is no such table in the
	 * given schema
	 * @throws SQLException
	 */
	public boolean existsTable(RelationalSchemaTableName schemaTable)
			throws SQLException {
		/* Get the connection if it has not been established. */
		this.getConnection();
		try {
			preparedSt = con.prepareStatement(
					"select exists (select 1 from information_schema.tables where table_schema=? and table_name=?)");
			preparedSt.setString(1, schemaTable.getSchemaName());
			preparedSt.setString(2, schemaTable.getTableName());
		} catch (SQLException e) {
			e.printStackTrace();
			log.error(e.getMessage()
					+ " SQLServerHandler, the query preparation for checking if a table exists failed.");
			throw e;
		}
		try {
			System.out.println(preparedSt);
			ResultSet rs = preparedSt.executeQuery();
			rs.next();
			return rs.getBoolean(1);
		} catch (SQLException e) {
			e.printStackTrace();
			log.error(e.getMessage() + " Failed to check if a table exists.");
			throw e;
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see istc.bigdawg.query.DBHandler#close()
	 */
	@Override
	public void close() throws Exception {
		cleanSQLServerResources();
	}
}
