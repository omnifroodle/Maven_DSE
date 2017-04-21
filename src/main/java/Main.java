import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.dse.DseCluster;
import com.datastax.driver.dse.DseSession;
import com.datastax.driver.core.*;
import com.datastax.driver.dse.graph.GraphResultSet;
import com.datastax.driver.dse.graph.GraphStatement;
import com.datastax.driver.dse.graph.SimpleGraphStatement;
// for async access
import com.datastax.driver.mapping.Mapper;
import com.datastax.driver.mapping.MappingManager;
import com.google.common.base.Function;
import com.google.common.util.concurrent.*;
// for name import
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;


public class Main {

    public static void main(String[] args) {
        String clusterAddress = "127.0.0.1";
        DseCluster cluster = null;
        try {

            cluster = DseCluster.builder()
                    .addContactPoint(clusterAddress)
                    .build();
            DseSession session = cluster.connect("search");

            // Get some information about the cluster
            getVersion(session);

            // Insert some rows into a column family
            String[] lastNames = getNames("last_names.csv");
            String[] firstNames = getNames("first_names.csv");
            executeBoundInsert(session, firstNames, lastNames);

            // Get a row from a Column Family in the cluster
            executeSelect(session);

            // Async Query
            executeAsyncQuery(cluster);

            String firstName = "Nancy";
            String lastName = "Smith";
            executeQueryBuilderStatement(session, firstName, lastName);

            executeMapperQuery(session, firstName, lastName);

            // Additional DSE features
            // Graph
            executeGraphQuery(session);

            // Solr Searching
            executeSolrSearch(session);
            // Very fuzzy Solr searching
            executeSolrPhoneticSearch(session);

        } finally {
            if (cluster != null) cluster.close(); 
        }
    }

    private static void getVersion(DseSession session) {
        Row row = session.execute("SELECT release_version FROM system.local").one(); // (3)
        System.out.format("Cassandra Release Version: %s\n", row.getString("release_version")); // (4)
    }

    private static void executeSelect(DseSession session) {
        System.out.println("Finding a row with a simple statement");

        ResultSet rs = session.execute("SELECT * FROM search.people WHERE last_name = 'Smith' AND first_name = 'John';");

        for (Row person : rs) {
            System.out.format("%s, %s\n", person.getString("last_name"), person.getString("first_name"));
        }
    }

    // From http://docs.datastax.com/en/developer/java-driver-dse/1.2/manual/statements/prepared/
    private static void executeBoundInsert(DseSession session, String[] first_names, String[] lastNames) {
        System.out.println("Insert a rows with a prepared statement");

        // Note that this should only be run once per session. The statement remains prepared on the cluster side
        PreparedStatement prepared = session.prepare("INSERT INTO search.people (last_name, first_name) VALUES (?, ?)");

        // insert a demo user we'll use later
        BoundStatement bound = prepared.bind("Smith", "John");
        session.execute(bound);

        // insert some other users with the same prepared statement
        for (int i = 0; i < 10000; i++) {
            BoundStatement loopbound = prepared.bind(randomString(lastNames), randomString(first_names));
            session.execute(loopbound);
        }
    }

    // From http://docs.datastax.com/en/developer/java-driver-dse/1.2/manual/async/
    private static void executeAsyncQuery(DseCluster cluster) {
        // For historical reasons, this returns a Future of Session, not DseSession. But you can safely cast to DseSession in
        // your callbacks if you need to access DSE-specific features. Here it doesn't really matter because we only use CQL
        // features.
        ListenableFuture<Session> session = cluster.connectAsync();

        // Use transform with an AsyncFunction to chain an async operation after another:
        ListenableFuture<ResultSet> resultSet = Futures.transformAsync(session,
                new AsyncFunction<Session, ResultSet>() {
                    public ListenableFuture<ResultSet> apply(Session session) throws Exception {
                        return session.executeAsync("SELECT release_version FROM system.local");
                    }
                });

        // Use transform with a simple Function to apply a synchronous computation on the result:
        ListenableFuture<String> version = Futures.transform(resultSet,
                new Function<ResultSet, String>() {
                    public String apply(ResultSet rs) {
                        return rs.one().getString("release_version");
                    }
                });

        // Use a callback to perform an action once the future is complete:
        Futures.addCallback(version, new FutureCallback<String>() {
            public void onSuccess(String version) {
                System.out.printf("DSE version: %s%n", version);
            }

            public void onFailure(Throwable t) {
                System.out.printf("Failed to retrieve the version: %s%n",
                        t.getMessage());
            }
        });
    }

    private static void executeQueryBuilderStatement(DseSession session, String firstName, String lastName) {
        Insert qbStatement = QueryBuilder.insertInto("search", "people")
            .value("first_name", firstName).value("last_name", lastName);
        ResultSetFuture rsf = session.executeAsync(qbStatement);

        // Wait for the query to succeed
        rsf.getUninterruptibly();
    }

    private static void executeMapperQuery(DseSession session, String firstName, String lastName) {
        MappingManager manager = new MappingManager(session);
        Mapper<Person> mapper = manager.mapper(Person.class);

        Person p = new Person(firstName, lastName);
        mapper.save(p);
    }

    // From http://docs.datastax.com/en/developer/java-driver-dse/1.2/manual/graph/
    private static void executeGraphQuery(DseSession session) {
        session.executeGraph("system.graph('graph_demo').ifNotExists().create()");

        GraphStatement s1 = new SimpleGraphStatement("g.addV(label, 'test_vertex')").setGraphName("graph_demo");
        session.executeGraph(s1);

        GraphStatement s2 = new SimpleGraphStatement("g.V()").setGraphName("graph_demo");
        GraphResultSet rs = session.executeGraph(s2);
        System.out.println(rs.one().asVertex());
    }

    private static void executeSolrSearch(DseSession session) {
        System.out.println("Searching for a value with DSE Solr");
        ResultSet rs = session.execute("SELECT COUNT(*) FROM search.people WHERE solr_query = 'last_name:Jones'");

        System.out.format("%d records for last_name Smith", rs.one().getLong(0));
    }

    private static void executeSolrPhoneticSearch(DseSession session) {
        //this requires a customer Solr Schema to perform phonetic encoding on the last_name field
        System.out.println("Searching for phonetic match with DSE Solr");

        ResultSet rs = session.execute("SELECT COUNT(*) FROM search.people WHERE solr_query = 'last_sounds:Jones'");

        System.out.format("%d records for last_name that sound like Smith", rs.one().getLong(0));
    }

    private static String[] getNames(String file) {
        Scanner sc = new Scanner(Main.class.getResourceAsStream("/" + file));
        List<String> lines = new ArrayList<String>();
        while (sc.hasNextLine()) {
            lines.add(sc.nextLine());
        }

        String[] arr = lines.toArray(new String[0]);
        return arr;
    }

    public static String randomString(String[] array) {
        int rnd = new Random().nextInt(array.length);
        return array[rnd];
    }
}
