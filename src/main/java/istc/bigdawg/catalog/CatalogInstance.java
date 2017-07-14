/**
 * 
 */
package istc.bigdawg.catalog;

import org.apache.log4j.Logger;

import istc.bigdawg.postgresql.PostgreSQLInstance;

/**
 * @author Adam Dziedzic
 * 
 * Singleton of the catalog instance.
 *
 */
public enum CatalogInstance {

	INSTANCE;
	private Logger logger = org.apache.log4j.Logger.getLogger(CatalogInstance.class.getName());
	private Catalog catalog;

	CatalogInstance() {
		catalog = new Catalog();
		try {
			System.out.println("Connecting to catalog:");
			System.out.println("==>> " + PostgreSQLInstance.URL);
			System.out.println("==>> " + PostgreSQLInstance.USER);
			System.out.println("==>> " + PostgreSQLInstance.PASSWORD);
			CatalogInitiator.connect(catalog, PostgreSQLInstance.URL, PostgreSQLInstance.USER,
					PostgreSQLInstance.PASSWORD);
			/*System.out.println("Connecting to catalog:");
			System.out.println("==>> " + SqlServerInstance.URL);
			System.out.println("==>> " + SqlServerInstance.USER);
			System.out.println("==>> " + SqlServerInstance.PASSWORD);
			CatalogInitiator.connect(catalog, SqlServerInstance.URL, SqlServerInstance.USER,
					SqlServerInstance.PASSWORD);*/
			
		} catch (Exception e) {
			String msg = "Catalog initialization failed!";
			System.err.println(msg);
			//logger.error(msg);
			e.printStackTrace();
			System.exit(1);
		}
	}

	public Catalog getCatalog() {
		return catalog;
	}

	public void closeCatalog() {
		try {
			CatalogInitiator.close(catalog);
		} catch (Exception e) {
			String msg="Catalog closing failed!";
			System.err.println(msg);
			logger.error(msg);
			e.printStackTrace();
		}
	}
}
