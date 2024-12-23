package gipf;

import static com.github.npathai.hamcrestopt.OptionalMatchers.hasValue;
import static com.github.npathai.hamcrestopt.OptionalMatchers.isEmpty;
import static com.github.npathai.hamcrestopt.OptionalMatchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

public class TournoiTest {
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource(
                            "10-create.sql"), "/docker-entrypoint-initdb.d/"
            );

    private static Connection con;

    private List<Joueur> arbitres;
    private List<Partie> parties;
    private Tournoi tournoi;

    @BeforeAll
    static void beforeAll() throws SQLException {
        postgres.start();
        var connectionProvider = new DBConnectionProvider(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
        con = connectionProvider.getConnection();
        con.setAutoCommit(true);
    }

    @AfterAll
    static void afterAll() {
        postgres.stop();
    }


    @BeforeEach
    void setUp() throws SQLException {
        try (Statement stmt = con.createStatement()) {
            stmt.executeUpdate("TRUNCATE TABLE Joueur, Partie, Arbitre, Tournoi");
        }
        List<Joueur> joueurs = JoueurTest.inscrire(con);

        parties = PartieTest.randParties(joueurs, new Random(0), con);

        arbitres = new ArrayList<>(joueurs.subList(0, 3));

        tournoi = Tournoi.create(LocalDate.of(2017, 3, 10), "Maubeuge", arbitres, con);
        assertThat(tournoi.getIdTournoi(), greaterThan(0));
    }

    @Test
    public void testQuote() throws SQLException, InscriptionException {
        Joueur j = Joueur.inscrire("to'to", "toto", "toto@univ.fr", con);
        Tournoi t = Tournoi.create(LocalDate.now(), "Villeneuve d'Ascq", Arrays.asList(j), con);
        assertThat(Tournoi.load(t.getIdTournoi(), con), hasValue(hasProperty("lieu", equalTo("Villeneuve d'Ascq"))));
        tournoi.addArbitre(j);
        tournoi.save(con);
        assertThat(Tournoi.load(tournoi.getIdTournoi(), con), hasValue(hasProperty("arbitres", hasItem(j))));
    }

    @Test
    public void testLoad() throws SQLException {
        Optional<Tournoi> ot = Tournoi.load(tournoi.getIdTournoi(), con);
        assertThat(ot, isPresent());
        Tournoi t = ot.get();
        assertEquals(tournoi.getArbitres(), t.getArbitres());
        assertEquals(tournoi.getIdTournoi(), t.getIdTournoi());
        assertEquals(tournoi.getDebut(), t.getDebut());
        assertEquals(tournoi.getFin(), t.getFin());
        assertEquals(tournoi.getLieu(), t.getLieu());
    }

    @Test
    public void testNoArbitres() throws SQLException {
        int id = Tournoi.create(LocalDate.of(2017, 3, 10), "Maubeuge", Collections.emptyList(), con).getIdTournoi();
        Tournoi t = Tournoi.load(id, con).get();
        assertThat(t.getArbitres(), empty());
    }

    @Test
    public void testSave() throws SQLException {
        tournoi.save(con);
        Tournoi t = Tournoi.load(tournoi.getIdTournoi(), con).get();
        assertEquals(tournoi.getArbitres(), t.getArbitres());
        assertEquals(tournoi.getIdTournoi(), t.getIdTournoi());
        assertEquals(tournoi.getDebut(), t.getDebut());
        assertEquals(tournoi.getFin(), t.getFin());
        assertEquals(tournoi.getLieu(), t.getLieu());
    }

    @Test
    public void testSetFin() throws SQLException {
        tournoi.setFin(LocalDate.of(2017, 3, 11));
        tournoi.save(con);

        assertEquals(tournoi.getFin(), Tournoi.load(tournoi.getIdTournoi(), con).get().getFin());
    }

    @Test
    public void testSetFinIncorrecte() throws SQLException {
        tournoi.setFin(LocalDate.of(2016, 3, 11));
        assertThrows(Exception.class, () -> tournoi.save(con));
    }

    @Test
    public void testAddArbitre() throws SQLException, InscriptionException {
        Joueur a = Joueur.inscrire("toto", "toto", "t@b.c", con);
        tournoi.addArbitre(a);
        tournoi.save(con);

        tournoi = Tournoi.load(tournoi.getIdTournoi(), con).get();

        arbitres.add(a);
        assertThat(tournoi.getArbitres(), containsInAnyOrder(arbitres.toArray()));

        tournoi.addArbitre(arbitres.get(0));
        tournoi.save(con);

        tournoi = Tournoi.load(tournoi.getIdTournoi(), con).get();
        assertThat(tournoi.getArbitres(), containsInAnyOrder(arbitres.toArray()));
    }

    @Test
    public void testRemoveArbitre() throws SQLException, InscriptionException {

        tournoi.removeArbitre(arbitres.get(0));
        tournoi.save(con);

        tournoi = Tournoi.load(tournoi.getIdTournoi(), con).get();

        Joueur removed = arbitres.get(0);

        arbitres.remove(removed);
        assertThat(tournoi.getArbitres(), containsInAnyOrder(arbitres.toArray()));

        try {
            tournoi.removeArbitre(removed);
        } catch (Exception e) {
            // Peut déclencher une exception
        }
        tournoi.save(con);

        tournoi = Tournoi.load(tournoi.getIdTournoi(), con).get();
        assertThat(tournoi.getArbitres(), containsInAnyOrder(arbitres.toArray()));
    }

    @Test
    public void testSetTournoi() throws SQLException {
        for (Partie p : parties.subList(0, 10)) {
            assertThat(p.getIdTournoi(), isEmpty());
            p.setTournoi(tournoi);
            p.save(con);
            assertThat(p.getIdTournoi(), hasValue(tournoi.getIdTournoi()));
        }
    }

    @Test
    public void testLoadParties() throws SQLException {
        for (Partie p : parties.subList(0, 10)) {
            p.setTournoi(tournoi);
            p.save(con);
        }

        assertThat(tournoi.loadParties(con), containsInAnyOrder(parties.subList(0, 10).toArray()));

    }
}
