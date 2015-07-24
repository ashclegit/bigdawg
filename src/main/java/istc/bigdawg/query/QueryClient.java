/**

 *
 */
package istc.bigdawg.query;

import istc.bigdawg.BDConstants;
import istc.bigdawg.accumulo.AccumuloInstance;
import istc.bigdawg.exceptions.NotSupportIslandException;
import istc.bigdawg.properties.BigDawgConfigProperties;
import istc.bigdawg.query.parser.Parser;
import istc.bigdawg.query.parser.simpleParser;
import istc.bigdawg.utils.Row;
import istc.bigdawg.utils.Tuple;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.Scanner;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.hadoop.io.Text;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * @author Adam Dziedzic
 * 
 *         tests: 1) curl -v -H "Content-Type: application/json" -X POST -d
 *         '{"query":"this is a query","authorization":{},"tuplesPerPage
 *         ":1,"pageNumber":1,"timestamp":"2012-04-23T18:25:43.511Z"}'
 *         http://localhost:8080/bigdawg/query 2) curl -v -H
 *         "Content-Type: application/json" -X POST -d '{"query":"select
 *         version(
 *         )","authorization":{},"tuplesPerPage":1,"pageNumber":1,"timestamp
 *         ":"2012-04-23T18:25:43.511Z"}' http://localhost:8080/bigdawg/query 3)
 *         curl -v -H "Content-Type: application/json" -X POST -d
 *         '{"query":"select * from
 *         authors","authorization":{},"tuplesPerPage":1,"
 *         pageNumber":1,"timestamp":"2012-04-23T18:25:43.511Z"}'
 *         http://localhost:8080/bigdawg/query
 */
@Path("/")
public class QueryClient {

	static org.apache.log4j.Logger log = org.apache.log4j.Logger
			.getLogger(QueryClient.class.getName());

	private Connection con = null;
	private Statement st = null;
	private ResultSet rs = null;
	private PreparedStatement pst = null;

	private String url;
	private String user;
	private String password;
	
	public QueryClient() {
		String database=BigDawgConfigProperties.INSTANCE.getPostgreSQLDatabase();
		String host=BigDawgConfigProperties.INSTANCE.getPostgreSQLHost();
		String port=BigDawgConfigProperties.INSTANCE.getPostgreSQLPort();
		this.url="jdbc:postgresql://"+host+":"+port+"/"+database;
		if (port == null) {
			this.url="jdbc:postgresql://"+host+"/"+database;
		}
		this.user=BigDawgConfigProperties.INSTANCE.getPostgreSQLUser();
		this.password=BigDawgConfigProperties.INSTANCE.getPostgreSQLPassword();
	}

	/**
	 * Answer a query from a client.
	 * 
	 * @param istream
	 * @return
	 * @throws AccumuloSecurityException 
	 * @throws AccumuloException 
	 * @throws TableNotFoundException 
	 */
	@Path("query")
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response query(String istream) throws TableNotFoundException, AccumuloException, AccumuloSecurityException {
		log.info("istream: " + istream);
		ObjectMapper mapper = new ObjectMapper();
		try {
			RegisterQueryRequest st = mapper.readValue(istream,
					RegisterQueryRequest.class);
			System.out.println(mapper.writeValueAsString(st));
			Parser parser = new simpleParser();
			String queryString = null;
			try {
				ASTNode parsed = parser.parseQueryIntoTree(st.getQuery());
				System.out.println(parsed.getShim());
				if (parsed.getShim() == BDConstants.Shim.ACCUMULOTEXT) {
					return Response.status(200).entity(executeQueryAccumulo("note_events_TedgeTxt")).build();
				}
				if (parsed.getShim() != BDConstants.Shim.PSQLRELATION) {
					RegisterQueryResponse resp = new RegisterQueryResponse(
							"ERROR: Unrecognized shim "
									+ parsed.getShim().toString(), 412, null,
							1, 1, null, null, new Timestamp(0));
					String responseResult = mapper.writeValueAsString(resp);
					return Response.status(412).entity(responseResult).build();
				}
				queryString = parsed.getTarget();
			} catch (NotSupportIslandException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			Tuple.Tuple3<List<String>, List<String>, List<List<String>>> result = executeQueryPostgres(queryString);
			List<String> colNames = result.getT1();
			List<String> colTypes = result.getT2();
			List<List<String>> rows = result.getT3();
			RegisterQueryResponse resp = new RegisterQueryResponse("OK", 200,
					rows, 1, 1, colNames, colTypes, new Timestamp(0));
			String responseResult = mapper.writeValueAsString(resp);
			return Response.status(200).entity(responseResult).build();
		} catch (UnrecognizedPropertyException e) {
			e.printStackTrace();
			return Response.status(500).entity(e.getMessage()).build();
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return Response.status(500).entity(e.getMessage()).build();
		} catch (IOException e) {
			e.printStackTrace();
			return Response.status(500).entity("yikes").build();
		}
	}

	private Tuple.Tuple3<List<String>, List<String>, List<List<String>>> executeQueryPostgres(
			final String query) {
		List<String> colNames = null;
		List<String> colTypes = null;
		List<List<String>> rows = new ArrayList<List<String>>();
		try {
			con = DriverManager.getConnection(url, user, password);
			st = con.createStatement();
			rs = st.executeQuery(query);
			if (rs == null)
				return null;
			final ResultSetMetaData rsmd = rs.getMetaData();
			colNames = Row.getColumnNames(rsmd);
			colTypes = Row.getColumnTypes(rsmd);
			List<Row> table = new ArrayList<Row>();
			Row.formTable(rs, table);
			for (Row row : table) {
				List<String> resultRowList = new ArrayList<String>();
				for (Entry<Object, Class> col : row.row) {
					System.out.print(" > "
							+ ((col.getValue()).cast(col.getKey())));
					resultRowList.add(col.getValue().cast(col.getKey())
							.toString());
				}
				System.out.println();
				rows.add(resultRowList);
			}
		} catch (SQLException ex) {
			Logger lgr = Logger.getLogger(QueryClient.class.getName());
			lgr.log(Level.SEVERE, ex.getMessage(), ex);
		} finally {
			try {
				if (rs != null) {
					rs.close();
				}
				if (st != null) {
					st.close();
				}
				if (con != null) {
					con.close();
				}
			} catch (SQLException ex) {
				Logger lgr = Logger.getLogger(QueryClient.class.getName());
				lgr.log(Level.WARNING, ex.getMessage(), ex);
			}
		}
		Tuple.Tuple3<List<String>, List<String>, List<List<String>>> result = new Tuple.Tuple3<List<String>, List<String>, List<List<String>>>(
				colNames, colTypes, rows);
		return result;
	}

	private void createAuthor(final String author) {
		try {
			con = DriverManager.getConnection(url, user, password);

			String stm = "INSERT INTO authors(name) VALUES(?)";
			pst = con.prepareStatement(stm);
			pst.setString(1, author);
			pst.executeUpdate();

		} catch (SQLException ex) {
			Logger lgr = Logger.getLogger(QueryClient.class.getName());
			lgr.log(Level.SEVERE, ex.getMessage(), ex);

		} finally {

			try {
				if (pst != null) {
					pst.close();
				}
				if (con != null) {
					con.close();
				}

			} catch (SQLException ex) {
				
				Logger lgr = Logger.getLogger(QueryClient.class.getName());
				lgr.log(Level.SEVERE, ex.getMessage(), ex);
			}
		}
	}

	public String executeQueryAccumulo(String table)
			throws TableNotFoundException, AccumuloException,
			AccumuloSecurityException, JsonProcessingException {
		// specify which visibilities we are allowed to see
		Authorizations auths = new Authorizations("public");
		AccumuloInstance accInst = AccumuloInstance.getInstance();
		Connector conn = accInst.getConnector();
		conn.securityOperations().changeUserAuthorizations(
				accInst.getUsername(), auths);
		Scanner scan = conn.createScanner(table, auths);
		scan.setRange(new Range("0", null));
		scan.fetchColumnFamily(new Text(""));
		List<List<String>> allRows = new ArrayList<List<String>>();
		for (Entry<Key, Value> entry : scan) {
			System.out.println(entry.getKey());
			System.out.println(entry.getValue());
			List<String> oneRow = new ArrayList<String>();
			Text rowIdResult = entry.getKey().getRow();
			Text colFamResult = entry.getKey().getColumnFamily();
			Text colKeyResult = entry.getKey().getColumnQualifier();
			Text visibility = entry.getKey().getColumnVisibility();
			Value valueResult = entry.getValue();
			oneRow.add(rowIdResult.toString());
			oneRow.add(colFamResult.toString());
			oneRow.add(colKeyResult.toString());
			oneRow.add(visibility.toString());
			oneRow.add(valueResult.toString());
			allRows.add(oneRow);
		}
		RegisterQueryResponse resp= new RegisterQueryResponse("OK", 200,
					allRows, 1, 1, new ArrayList<String>(), new ArrayList<String>(), new Timestamp(0));
		ObjectMapper mapper = new ObjectMapper();
		return mapper.writeValueAsString(resp);
	}

	public static void main(String[] args) {
		QueryClient qClient = new QueryClient();
		// qClient.executeQueryPostgres("Select * from books");
		// Response response = qClient
		// .query("{\"query\":\"RELATION(select * from mimic2v26.d_patients limit 5)\",\"authorization\":{},\"tuplesPerPage\":1,\"pageNumber\":1,\"timestamp\":\"2012-04-23T18:25:43.511Z\"}");
		// System.out.println(response.getEntity());
		try {
			try {
				qClient.executeQueryAccumulo("note_events_TedgeTxt");
			} catch (JsonProcessingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (TableNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (AccumuloException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (AccumuloSecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
