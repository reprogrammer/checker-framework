import java.util.*;

import org.checkerframework.checker.nullness.qual.*;

// See issue 241: https://code.google.com/p/checker-framework/issues/detail?id=241
//@skip-test
public class JavaExprContext {
  
  public static class Graph {
    private Map<String, Integer> adjList = new HashMap<String, Integer>();

    public void addEdge(@KeyFor("adjList") String source) {
    }

    public void addEdge2(@KeyFor("this.adjList") String source) {
    }

  }

  public static class MarvelPaths {

    public static void buildGraph1(Graph myGraph, @KeyFor("#1.adjList") String hero) {
      myGraph.addEdge(hero);
      myGraph.addEdge2(hero);
    }

    static Graph graphField = new Graph();

    public static void buildGraph2(@KeyFor("graphField.adjList") String hero) {
      graphField.addEdge(hero);
      graphField.addEdge2(hero);
    }

  }

}
