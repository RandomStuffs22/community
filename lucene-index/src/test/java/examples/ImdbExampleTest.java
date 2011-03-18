/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package examples;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.graphdb.DynamicRelationshipType;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.index.IndexManager;
import org.neo4j.graphdb.index.RelationshipIndex;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.index.Neo4jTestCase;
import org.neo4j.index.impl.lucene.QueryContext;
import org.neo4j.index.impl.lucene.ValueContext;
import org.neo4j.kernel.EmbeddedGraphDatabase;

public class ImdbExampleTest
{
    private static GraphDatabaseService graphDb;
    private Transaction tx;

    @BeforeClass
    public static void setUpDb()
    {
        Neo4jTestCase.deleteFileOrDirectory( new File( "target/graphdb" ) );
        graphDb = new EmbeddedGraphDatabase( "target/graphdb" );
        Transaction transaction = graphDb.beginTx();
        try
        {
            // START SNIPPET: createIndices
            IndexManager index = graphDb.index();
            Index<Node> actors = index.forNodes( "actors" );
            Index<Node> movies = index.forNodes( "movies" );
            RelationshipIndex roles = index.forRelationships( "roles" );
            // END SNIPPET: createIndices

            // START SNIPPET: createNodes
            // Actors
            Node reeves = graphDb.createNode();
            actors.add( reeves, "name", "Keanu Reeves" );
            Node bellucci = graphDb.createNode();
            actors.add( bellucci, "name", "Monica Bellucci" );
            // multiple values for a field
            actors.add( bellucci, "name", "La Bellucci" );
            // Movies
            Node theMatrix = graphDb.createNode();
            movies.add( theMatrix, "title", "The Matrix" );
            movies.add( theMatrix, "title", "Matrix" );
            movies.add( theMatrix, "year", 1999 );
            Node theMatrixReloaded = graphDb.createNode();
            movies.add( theMatrixReloaded, "title", "The Matrix Reloaded" );
            movies.add( theMatrixReloaded, "year", 2003 );
            Node speed = graphDb.createNode();
            movies.add( speed, "title", "Speed" );
            movies.add( theMatrix, "year", 1994 );
            // END SNIPPET: createNodes

            reeves.setProperty( "name", "Keanu Reeves" );
            bellucci.setProperty( "name", "Monica Bellucci" );
            theMatrix.setProperty( "title", "The Matrix" );
            theMatrix.setProperty( "year", 1999 );
            theMatrixReloaded.setProperty( "title", "The Matrix Reloaded" );
            theMatrixReloaded.setProperty( "year", 2003 );
            speed.setProperty( "title", "Speed" );
            speed.setProperty( "year", 1994 );

            // START SNIPPET: createRelationships
            // we need a relationship type
            DynamicRelationshipType ACTS_IN = DynamicRelationshipType.withName( "ACTS_IN" );
            // create relationships
            Relationship role1 = reeves.createRelationshipTo( theMatrix, ACTS_IN );
            roles.add( role1, "name", "Neo" );
            Relationship role2 = reeves.createRelationshipTo( theMatrixReloaded, ACTS_IN );
            roles.add( role2, "name", "Neo" );
            Relationship role3 = bellucci.createRelationshipTo(
                    theMatrixReloaded, ACTS_IN );
            roles.add( role3, "name", "Persephone" );
            // END SNIPPET: createRelationships

            role1.setProperty( "name", "Neo" );
            role2.setProperty( "name", "Neo" );
            role3.setProperty( "name", "Persephone" );

            transaction.success();
        }
        finally
        {
            transaction.finish();
        }
    }

    @AfterClass
    public static void tearDownDb()
    {
        graphDb.shutdown();
    }

    @Before
    public void beginTx()
    {
        tx = graphDb.beginTx();
    }

    @After
    public void finishTx()
    {
        tx.finish();
    }

    private void rollbackTx()
    {
        finishTx();
        beginTx();
    }

    @Test
    public void checkIfIndexExists()
    {
        // START SNIPPET: checkIfExists
        IndexManager index = graphDb.index();
        boolean indexExists = index.existsForNodes( "actors" );
        // END SNIPPET: checkIfExists
        assertTrue( indexExists );
    }

    @Test
    public void removeFromIndex()
    {
        IndexManager index = graphDb.index();
        Index<Node> movies = index.forNodes( "movies" );
        Node theMatrix = movies.get( "title", "The Matrix" ).getSingle();

        // START SNIPPET: removeNodeFromIndex
        // completely remove theMatrix from the movies index
        movies.remove( theMatrix );
        // END SNIPPET: removeNodeFromIndex

        Node node = movies.get( "title", "The Matrix" ).getSingle();
        assertEquals( null, node );

        rollbackTx();

        // START SNIPPET: removeNodeFromIndex
        // remove any "title" entry of theMatrix from the movies index
        movies.remove( theMatrix, "title" );
        // END SNIPPET: removeNodeFromIndex

        node = movies.get( "title", "The Matrix" ).getSingle();
        assertEquals( null, node );
        node = movies.get( "title", "Matrix" ).getSingle();
        assertEquals( null, node );
        node = movies.get( "year", 1999 ).getSingle();
        assertEquals( theMatrix, node );

        rollbackTx();

        // START SNIPPET: removeNodeFromIndex
        // remove the "title" -> "Matrix" entry of theMatrix
        movies.remove( theMatrix, "title", "Matrix" );
        // END SNIPPET: removeNodeFromIndex

        node = movies.get( "title", "Matrix" ).getSingle();
        assertEquals( null, node );
        node = movies.get( "title", "The Matrix" ).getSingle();
        assertEquals( theMatrix, node );
        node = movies.get( "year", 1999 ).getSingle();
        assertEquals( theMatrix, node );
    }

    @Test
    public void update()
    {
        IndexManager index = graphDb.index();
        Index<Node> actors = index.forNodes( "actors" );

        // START SNIPPET: update
        // create a node with a property
        Node fishburn = graphDb.createNode();
        fishburn.setProperty( "name", "Fishburn" );
        // index it
        actors.add( fishburn, "name", fishburn.getProperty( "name" ) );
        // END SNIPPET: update

        Node node = actors.get( "name", "Fishburn" ).getSingle();
        assertEquals( fishburn, node );

        // START SNIPPET: update
        // update the index entry
        actors.remove( fishburn, "name", fishburn.getProperty( "name" ) );
        fishburn.setProperty( "name", "Laurence Fishburn" );
        actors.add( fishburn, "name", fishburn.getProperty( "name" ) );
        // END SNIPPET: update

        node = actors.get( "name", "Fishburn" ).getSingle();
        assertEquals( null, node );
        node = actors.get( "name", "Laurence Fishburn" ).getSingle();
        assertEquals( fishburn, node );
    }

    @Test
    public void doGetForNodes()
    {
        Index<Node> actors = graphDb.index().forNodes( "actors" );

        // START SNIPPET: getSingleNode
        IndexHits<Node> hits = actors.get( "name", "Keanu Reeves" );
        Node reeves = hits.getSingle();
        // END SNIPPET: getSingleNode

        assertEquals( "Keanu Reeves", reeves.getProperty( "name" ) );
    }

    // @Test
    // public void getSameFromDifferentValuesO

    @Test
    public void doGetForRelationships()
    {
        RelationshipIndex roles = graphDb.index().forRelationships( "roles" );

        // START SNIPPET: getSingleRelationship
        Relationship persephone = roles.get( "name", "Persephone" ).getSingle();
        Node actor = persephone.getStartNode();
        Node movie = persephone.getEndNode();
        // END SNIPPET: getSingleRelationship

        assertEquals( "Monica Bellucci", actor.getProperty( "name" ) );
        assertEquals( "The Matrix Reloaded", movie.getProperty( "title" ) );

        @SuppressWarnings( "serial" )
        List<String> expectedActors = new ArrayList<String>()
        {
            {
                add( "Keanu Reeves" );
                add( "Keanu Reeves" );
            }
        };
        List<String> foundActors = new ArrayList<String>();

        // START SNIPPET: getRelationships
        for ( Relationship role : roles.get( "name", "Neo" ) )
        {
            // this will give us Reeves twice
            Node reeves = role.getStartNode();
            // END SNIPPET: getRelationships
            foundActors.add( (String) reeves.getProperty( "name" ) );
            // START SNIPPET: getRelationships
        }
        // END SNIPPET: getRelationships

        assertEquals( expectedActors, foundActors );
    }

    @Test
    public void doQueriesForNodes()
    {
        IndexManager index = graphDb.index();
        Index<Node> actors = index.forNodes( "actors" );
        Index<Node> movies = index.forNodes( "movies" );
        Set<String> found = new HashSet<String>();
        @SuppressWarnings( "serial" )
        Set<String> expectedActors = new HashSet<String>()
        {
            {
                add( "Monica Bellucci" );
                add( "Keanu Reeves" );
            }
        };
        @SuppressWarnings( "serial" )
        Set<String> expectedMovies = new HashSet<String>()
        {
            {
                add( "The Matrix" );
            }
        };

        // START SNIPPET: actorsQuery
        for ( Node actor : actors.query( "name", "*e*" ) )
        {
            // This will return Reeves and Bellucci
            // END SNIPPET: actorsQuery
            found.add( (String) actor.getProperty( "name" ) );
            // START SNIPPET: actorsQuery
        }
        // END SNIPPET: actorsQuery
        assertEquals( expectedActors, found );
        found.clear();

        // START SNIPPET: matrixQuery
        for ( Node movie : movies.query( "title:*Matrix* AND year:1999" ) )
        {
            // This will return "The Matrix" from 1999 only.
            // END SNIPPET: matrixQuery
            found.add( (String) movie.getProperty( "title" ) );
            // START SNIPPET: matrixQuery
        }
        // END SNIPPET: matrixQuery
        assertEquals( expectedMovies, found );

        // START SNIPPET: matrixSingleQuery
        Node matrix = movies.query( "title:*Matrix* AND year:2003" ).getSingle();
        // END SNIPPET: matrixSingleQuery
        assertEquals( "The Matrix Reloaded", matrix.getProperty( "title" ) );

        // START SNIPPET: queryWithScore
        IndexHits<Node> hits = movies.query( "title", "The*" );
        for ( Node movie : hits )
        {
            System.out.println( movie.getProperty( "title" ) + " "
                    + hits.currentScore() );
            // END SNIPPET: queryWithScore
            assertTrue( ( (String) movie.getProperty( "title" ) ).startsWith( "The" ) );
            // START SNIPPET: queryWithScore
        }
        // END SNIPPET: queryWithScore
        assertEquals( 2, hits.size() );

        // START SNIPPET: queryWithRelevance
        hits = movies.query( "title",
                new QueryContext( "The Matrix*" ).sort( Sort.RELEVANCE ) );
        // END SNIPPET: queryWithRelevance
        float previous = Float.MAX_VALUE;
        // START SNIPPET: queryWithRelevance
        for ( Node movie : hits )
        {
            // hits sorted by relevance (score)
            // END SNIPPET: queryWithRelevance
            assertTrue( hits.currentScore() <= previous );
            previous = hits.currentScore();
            // START SNIPPET: queryWithRelevance
        }
        // END SNIPPET: queryWithRelevance
        // TODO check number of hits

        // START SNIPPET: termQuery
        // a TermQuery will give exact matches
        Node actor = actors.query(
                new TermQuery( new Term( "name", "Keanu Reeves" ) ) ).getSingle();
        // END SNIPPET: termQuery
        assertEquals( "Keanu Reeves", actor.getProperty( "name" ) );

        Node theMatrix = movies.get( "title", "The Matrix" ).getSingle();
        Node theMatrixReloaded = movies.get( "title", "The Matrix Reloaded" ).getSingle();

        // START SNIPPET: wildcardTermQuery
        hits = movies.query( new WildcardQuery( new Term( "title",
        "The Matrix*" ) ) );
        for ( Node movie : hits )
        {
            System.out.println( movie.getProperty( "title" ) );
            // END SNIPPET: wildcardTermQuery
            assertTrue( ( (String) movie.getProperty( "title" ) ).startsWith( "The Matrix" ) );
            // START SNIPPET: wildcardTermQuery
        }
        // END SNIPPET: wildcardTermQuery
        assertEquals( 2, hits.size() );

        // START SNIPPET: numericRange
        movies.add( theMatrix, "year-numeric",
                new ValueContext( 1999L ).indexNumeric() );
        movies.add( theMatrixReloaded, "year-numeric",
                new ValueContext( 2003L ).indexNumeric() );

        // Query for range
        long startYear = 1997;
        long endYear = 2001;
        hits = movies.query( NumericRangeQuery.newLongRange( "year-numeric",
                startYear, endYear, true, true ) );
        // END SNIPPET: numericRange
        assertEquals( theMatrix, hits.getSingle() );

        hits = movies.query( "title:*Matrix* AND year:1999" );

        assertEquals( theMatrix, hits.getSingle() );

        // TODO finish test
        // hits = movies.query( new QueryContext( "title:*Matrix* AND year:1999"
        // ).sort(
        // "title", "year" ) );
        //
        // assertEquals( theMatrix, hits.getSingle() );

    }

    @Test
    public void doQueriesForRelationships()
    {
        IndexManager index = graphDb.index();
        RelationshipIndex roles = index.forRelationships( "roles" );
        Index<Node> actors = graphDb.index().forNodes( "actors" );
        Index<Node> movies = index.forNodes( "movies" );

        Node reeves = actors.get( "name", "Keanu Reeves" ).getSingle();
        Node theMatrix = movies.get( "title", "The Matrix" ).getSingle();

        // START SNIPPET: queryForRelationships
        // find relationships filtering on start node
        // using exact matches
        IndexHits<Relationship> reevesAsNeoHits;
        reevesAsNeoHits = roles.get( "name", "Neo", reeves, null );
        Relationship reevesAsNeo = reevesAsNeoHits.iterator().next();
        reevesAsNeoHits.close();
        // END SNIPPET: queryForRelationships
        assertEquals( "Neo", reevesAsNeo.getProperty( "name" ) );
        Node actor = reevesAsNeo.getStartNode();
        assertEquals( reeves, actor );

        // START SNIPPET: queryForRelationships
        // find relationships filtering on end node
        // using a query
        IndexHits<Relationship> matrixNeoHits;
        matrixNeoHits = roles.query( "name", "*eo", null, theMatrix );
        Relationship matrixNeo = matrixNeoHits.iterator().next();
        matrixNeoHits.close();
        // END SNIPPET: queryForRelationships
        assertEquals( "Neo", matrixNeo.getProperty( "name" ) );
        actor = matrixNeo.getStartNode();
        assertEquals( reeves, actor );

        // START SNIPPET: queryForRelationshipType
        // find relationships filtering on end node
        // using a relationship type.
        // this is how to add it to the index:
        roles.add( reevesAsNeo, "type", reevesAsNeo.getType().name() );
        // and now we can search for it:
        IndexHits<Relationship> typeHits;
        typeHits = roles.get( "type", "ACTS_IN", null, theMatrix );
        Relationship typeNeo = typeHits.iterator().next();
        typeHits.close();
        // END SNIPPET: queryForRelationshipType
        assertEquals( "Neo", typeNeo.getProperty( "name" ) );
        actor = matrixNeo.getStartNode();
        assertEquals( reeves, actor );
    }

    @Test
    public void fulltext()
    {
        // START SNIPPET: fulltext
        IndexManager index = graphDb.index();
        Index<Node> fulltextMovies = index.forNodes( "movies-fulltext",
                MapUtil.stringMap( "provider", "lucene", "type", "fulltext" ) );
        // END SNIPPET: fulltext

        Index<Node> movies = index.forNodes( "movies" );
        Node theMatrix = movies.get( "title", "The Matrix" ).getSingle();
        Node theMatrixReloaded = movies.get( "title", "The Matrix Reloaded" ).getSingle();

        // START SNIPPET: fulltext
        fulltextMovies.add( theMatrix, "title", "The Matrix" );
        fulltextMovies.add( theMatrixReloaded, "title", "The Matrix Reloaded" );
        // search in the fulltext index
        Node found = fulltextMovies.query( "title", "relOaded" ).getSingle();
        // END SNIPPET: fulltext
        assertEquals( theMatrixReloaded, found );
    }
}