package cpen221.mp3.wikimediator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Stack;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;

import cpen221.mp3.cache.Cache;
import cpen221.mp3.cache.CacheObject;
import cpen221.mp3.cache.NotFoundException;
import fastily.jwiki.core.Wiki;

public class WikiMediator {
    /*
     RI: methodNames is not null and contains all public methods within the WikiMediator Class
         wiki is not null and is the English domain of Wikipedia
         cache is not null
         timeMap is not null. All times in the map must be after this.startTime
         requestMap is not null. All times in the map must be after this.startTime
         startTime is not null
     */

    /*
    AF(wm): A mediator between the user and wikipedia such that
            cache is the cache used by the WikiMediator
            wiki is the instance of wikipedia used by the wikiMediator.
            timeMap is a map of all searches/queries that are made to the times that they were made.
            requestMap is a map of all method calls to the times that said methods were called.
            methodNames is an array of all non-constructor public methods.
     */

    /*
    Thread Safety Arguments:
       timeMap: every time the timeMap is accessed, it is wrapped into a synchronized block to
       protect the map from being added to or removed. All actions of the timeMap are atomic
       since we use a concurrent hashMap which prevents certain data races.

       requestMap: every time the request map is accessed, it is wrapped into a synchronized block
       to protect the map from being added to or removed while being read. All actions of the
       timeMap are atomic since we use a concurrent hashMap which prevents certain data races.

       cache: is never changed, only accessed and is made thread safe in the cache class
       startTime: is never edited, thus no need to be synchronized as it is only read from

       methodNames: are never edited, thus no need to be synchronized as they are only read from

       timeMapFile and requestMapFile: are final and immutable types, thus are thread safe


       Methods!!

       simpleSearch: this method is thread safe since we use synchronized blocks and critical
       sections to prevent data races. Furthermore, the addToMap function is synchronized,
       thus only one thread can add to the timeMap at one time. In addition, the requestMap portion
       is synchronized such that only one method can add to the requestMap at a time. When using
       the wiki to search, since we are reading this data, this does not to be made thread safe and
       doesn't cause a data race since it is local variable and is only reading from the wikipedia.

       getPage: this method is thread safe because we use synchronized blocks and critical sections
       to prevent data races. Furthermore, the addToMap function is synchronized and thus only one
       thread can add to the timeMap at one time. In addition, the requestMap portion is
       synchronized such that only one method can add to the requestMap at a time. When accessing
       wikipedia or the cache, since the text variable is local and immutable, it is also thread
       safe and thus should return the right value.

       getConnectedHops: this method is thread safe because we use synchronized blocks around the
       section that accesses the requestMap. Since each thread will have its own function call
       stack, the recursive helper is also thread-safe since each thread will have it's own call
       stack and local variables.

       zeitgeist: this method is thread safe because we use synchronized blocks around the sections
       that access the timeMap. Furthermore, while iterating over the timeMap, this section
       is a critical section and thus is wrapped in a synchronized block. The variables within
       the zeitgeist method themselves are threadsafe since each thread will have it's own local
       function variables.

       trending: this method is thread safe because we use synchronized blocks around the sections
       that access the timeMap. Furthermore, while iterating over the timeMap, this section
       is a critical section and thus is wrapped in a synchronized block to prevent other
       methods from writing to the map. The variables within the trending method are thread safe
       since each thread will have it's own local function variables.

       peakLoad30s: this method is thread safe because we use synchronized blocks around the
       sections that access the requestMap. Furthermore, while iterating over the intervals,
       this section is wrapped in a synchronized block and thus allows us to gain stats safely.
       The variables within the peakLoad30s are thread safe since they are local variables.

       getPath: this method is thread safe because we use synchronized blocks around the section
       that accesses the requestMap. Furthermore, the remaining variables used within the
       method are local variables and are threadsafe types, thus are thread safe.

       executeQuery: this method is thread safe because we use synchronized blocks are the
       section that accesses the requestMap. Furthermore, any variables or methods called
       in executeQuery are thread safe because they are local function calls in to a thread's
       own call stack.

       parse: this method is thread safe because we don't access any of the wikiMediator variables
     */

    /* Default Cache Capacity */
    private static final int DEFAULTCAPACITY = 256;

    /* Default Cache Expiry Time */
    private static final int DEFAULTTIMEOUT = 43200;

    /* The Wikipedia Instance of the WikiMediator */
    private Wiki wiki;

    /* The Cache Instance of the WikiMediator */
    private Cache cache;

    /* The time map of searches and queries (strings) to the time they were made */
    private Map<String, List<LocalDateTime>> timeMap;

    /* The request map of a method to the time the method was called */
    private Map<String, List<LocalDateTime>> requestMap;

    /* The starting time of the WikiMediator */
    private LocalDateTime startTime;

    /* The names of all methods in the WikiMediator Class */
    private final String[] methodNames =
            new String[]{"simpleSearch", "getPage", "getConnectedPages", "zeitgeist",
                    "trending", "peakLoad30s", "getPath", "executeQuery"};

    /* File names with which we save data to disc */
    private final String timeMapFile = "local/timeMapFile";
    private final String requestMapFile = "local/requestMapFile";
    private final String startTimeFile = "local/startTimeFile";

    /**
     * Constructs an instance of the WikiMediator.
     * This constructor creates a new English Wikipedia access, a new default cache object
     * and creates appropriate maps to store statistics in the WikiMediator
     *
     */
    public WikiMediator() {
        this.wiki = new Wiki("en.wikipedia.org");
        this.wiki.enableLogging(false);
        this.timeMap = new ConcurrentHashMap<>();
        this.requestMap = new ConcurrentHashMap<>();
        this.cache = new Cache<>(WikiMediator.DEFAULTCAPACITY, WikiMediator.DEFAULTTIMEOUT);
        this.startTime = LocalDateTime.now();

        /* adds the method names into the requestMap */
        for (String name : this.methodNames) {
            this.requestMap.put(name, Collections.synchronizedList(new ArrayList<>()));
        }
    }


    /**
     * Constructs an instance of the WikiMediator that uses an existing Cache object
     * Creates a new English Wikipedia access point, and initializes appropriate maps to store
     * statistics in the WikiMediator instance
     * @param cache is not null
     *
     */
    public WikiMediator(Cache cache) {
        this.wiki = new Wiki("en.wikipedia.org");
        this.wiki.enableLogging(false);
        this.timeMap = new ConcurrentHashMap<>();
        this.requestMap = new ConcurrentHashMap<>();
        this.cache = cache;
        this.startTime = LocalDateTime.now();

        /* adds the method names into the requestMap */
        for (String name : this.methodNames) {
            this.requestMap.put(name, Collections.synchronizedList(new ArrayList<>()));
        }
    }

    /**
     * A simple search function that returns a list of pages that match the query string
     * @param query is not null and is a String to use with Wikipedia's search function
     * @param limit >= 0 is the maximum number of items that simpleSearch will return
     * @modifies requestMap, adds a time the method was called into the request map
     * (under "simpleSearch" key)
     * @return  a list of strings with  size <= limit that appear from the query using
     * wikipedia's search service.
     * If limit is equal to 0, returns an empty list of strings
     */
    public List<String> simpleSearch(String query, int limit) {
        synchronized (this) {
            List<LocalDateTime> requestDates = this.requestMap.get("simpleSearch");
            requestDates.add(LocalDateTime.now());
            this.requestMap.replace("simpleSearch", requestDates);
        }

        addToMap(query);
        List<String> searches = new ArrayList<>();

        if (limit == 0) {
            return searches;
        } else {
            searches = this.wiki.search(query, limit);
            return searches;
        }
    }

    /**
     * Returns the page text of a given page title. If the page title has been requested already
     * the page text will be obtained from the cache instead of accessing wikipedia.
     * @param pageTitle is not null and is a page that we wish to find the wikipedia page for.
     * @modifies requestMap, adds a time the method was called into the request map
     * (under "getPage" key)
     * @return a string that contains the text of the given page title.
     * If page title is invalid, getPage follows the behaviour of the jWiki API
     */
    public String getPage(String pageTitle) {
        synchronized (this) {
            List<LocalDateTime> requestDates = this.requestMap.get("getPage");
            requestDates.add(LocalDateTime.now());
            this.requestMap.replace("getPage", requestDates);
        }

        String text;
        addToMap(pageTitle);

        try {
            CacheObject co = (CacheObject) this.cache.get(pageTitle);
            text = co.getText();
        } catch (NotFoundException e) {
            text = this.wiki.getPageText(pageTitle);
            this.cache.put(new CacheObject(pageTitle, text));
        }

        return text;
    }

    /**
     * Helper method to add a string request to the instance time map.
     * Method is synchronized so only one thread can access and add to map at the same time
     * @param request the query or pageTitle to be added to the map
     * @modifies timeMap, adds a query or pageTitle if it is not in the map,
     *                    otherwise, adds the current time to the list of times the
     *                    string has been used
     */
    private synchronized void addToMap(String request) {

        if (!this.timeMap.containsKey(request)) {
            List<LocalDateTime> timeList = Collections.synchronizedList(new ArrayList<>());
            timeList.add(LocalDateTime.now());
            this.timeMap.put(request, timeList);
        } else {
            List<LocalDateTime> timeList = this.timeMap.get(request);
            timeList.add(LocalDateTime.now());
            this.timeMap.replace(request, timeList);
        }
    }

    /**
     * Find a list of pages that are connected to the given page in a certain number of hops
     * @param pageTitle is not null and is the starting page
     * @param hops >= 0 and is the number of links that we jump through
     * @modifies requestMap, adds a time the method was called into the request map
     * (under "getConnectedPages" key)
     * @return A list of pages that are reachable within a certain number of hops from pageTitle.
     * This list contains no duplicate pages and thus, only returns a list of unique pages that can
     * be found through links from the initial pageTitle
     */
    public List<String> getConnectedPages(String pageTitle, int hops) {
        synchronized (this) {
            List<LocalDateTime> requestDates = this.requestMap.get("getConnectedPages");
            requestDates.add(LocalDateTime.now());
            this.requestMap.replace("getConnectedPages", requestDates);
        }

        Set<String> pageLinks = new HashSet<>();
        pageLinks.add(pageTitle);

        if (hops == 0) {
            return new ArrayList<>(pageLinks);
        } else {
            pageLinks.addAll(getConnectedPagesHelper(pageTitle, hops));
        }

        ArrayList<String> connectedPages = new ArrayList<>(pageLinks);
        Collections.sort(connectedPages);

        return connectedPages;
    }

    /**
     * Recursive Helper method for getConnectedPages
     * Base Case is if hops <=0, returns a list of just the current page title
     * otherwise subtracts 1 from hops and calls helper again for each link in the list
     * @param pageTitle is not null and is the initial page we are starting from
     * @param hops >= 0 and is the number of links to jump through
     * @return a list of pages that can be found within a certain number of hops
     * starting from pageTitle
     */
    private List<String> getConnectedPagesHelper(String pageTitle, int hops) {
        List<String> allPages = new ArrayList<>();
        List<String> titleOnly = new ArrayList<>();

        if (hops <= 0) {
            titleOnly.add(pageTitle);
            return titleOnly;
        }

        allPages.addAll(this.wiki.getLinksOnPage(pageTitle));
        hops--;
        for (String title : this.wiki.getLinksOnPage(pageTitle)) {
            allPages.addAll(getConnectedPagesHelper(title, hops));
        }

        return allPages;
    }

    /**
     * Returns a list of the most common strings used in the simpleSearch and getPage methods
     * @param limit >= 0 and is the maximum number of items to return from the method call
     * @modifies requestMap, adds a time the method was called into the request map
     * (under "zeitgeist" key)
     * @return a list of strings where strings are sorted by the amount of times they have been
     * called by the getPage or simple search method. These strings are sorted into non-increasing
     * order of appearance.
     * If limit = 0, returns an empty list of strings
     */
    public List<String> zeitgeist(int limit) {
        synchronized (this) {
            List<LocalDateTime> requestDates = this.requestMap.get("zeitgeist");
            requestDates.add(LocalDateTime.now());
            this.requestMap.replace("zeitgeist", requestDates);
        }

        synchronized (this) {
            if (this.timeMap.keySet().isEmpty()) {
                return new ArrayList<>();
            }
        }

        List<String> mostCommon = new ArrayList<>();
        int maxOccurrences;
        int count = 0;
        String mostOccurringSearch = "";

        synchronized (this) {
            while (count < limit) {
                maxOccurrences = 0;
                for (String search : this.timeMap.keySet()) {
                    if ((this.timeMap.get(search).size() > maxOccurrences)
                            && !mostCommon.contains(search)) {
                        maxOccurrences = this.timeMap.get(search).size();
                        mostOccurringSearch = search;
                    }
                }
                count++;
                if (!mostCommon.contains(mostOccurringSearch)) {
                    mostCommon.add(mostOccurringSearch);
                } else {
                    break;
                }
            }
        }

        return mostCommon;
    }

    /**
     * Returns a list of the most common Strings used in the getPage and simpleSearch method
     * from the past 30 seconds
     * @param limit >= 0 and is the maximum number of items to return from method call
     * @modifies requestMap, adds a time the method was called into the request map
     * (under "trending" key)
     * @return a list of strings where strings are sorted by the amount of times they have been
     * called by the getPage or simple search method. These strings are sorted into non-increasing
     * order of appearance.
     * If limit = 0, returns an empty list of strings
     */
    public List<String> trending(int limit) {
        synchronized (this) {
            List<LocalDateTime> requestDates = this.requestMap.get("trending");
            requestDates.add(LocalDateTime.now());
            this.requestMap.replace("trending", requestDates);
        }

        List<String> trendingList = new ArrayList<>();
        LocalDateTime currentTime = LocalDateTime.now();
        Map<String, Integer> frequencyList = new ConcurrentHashMap<>();

        synchronized (this) {
            if (this.timeMap.keySet().isEmpty()) {
                return new ArrayList<>();
            }
        }

        synchronized (this) {
            for (String request : this.timeMap.keySet()) {
                List<LocalDateTime> requestList = this.timeMap.get(request);
                int count = 0;
                for (LocalDateTime time : requestList) {
                    LocalDateTime compareTime = currentTime.minusSeconds(30);
                    if (time.isAfter(compareTime)) {
                        count++;
                    }
                }
                frequencyList.put(request, count);
            }
        }

        int limitCount = 0;
        int maxFrequency;
        String frequencyString = "";

        while (limitCount < limit) {
            maxFrequency = 0;
            for (String request : frequencyList.keySet()) {
                if (frequencyList.get(request) > maxFrequency && !trendingList.contains(request)) {
                    frequencyString = request;
                    maxFrequency = frequencyList.get(request);
                }
            }

            limitCount++;
            if (!trendingList.contains(frequencyString)) {
                trendingList.add(frequencyString);
            } else {
                break;
            }
        }

        return trendingList;
    }

    /**
     * Returns the maximum number of requests in any 30 second interval during the duration of
     * an instance of WikiMediator
     * @modifies requestMap, adds a time the method was called into the request map
     * (under "peakLoad30s" key)
     * @return the maximum number of request in any 30 second interval.
     * The return value will always be >= 1 since we consider the method call of peakLoad30s
     * to be within the last 30 seconds
     *
     */
    public int peakLoad30s() {
        synchronized (this) {
            List<LocalDateTime> requestDates = this.requestMap.get("peakLoad30s");
            requestDates.add(LocalDateTime.now());
            this.requestMap.replace("peakLoad30s", requestDates);
        }

        LocalDateTime startingTime = this.startTime;
        LocalDateTime endTime = LocalDateTime.now().minusSeconds(29);
        int maxLoad = 0;
        List<Integer> intervalRequestsList = new ArrayList<>();

        synchronized (this) {
            if (endTime.isBefore(startingTime)) {
                for (String request : this.requestMap.keySet()) {
                    maxLoad += this.requestMap.get(request).size();
                }
                return maxLoad;
            }
        }

        synchronized (this) {
            while (startingTime.isBefore(endTime)) {
                int intervalRequests = 0;
                LocalDateTime intervalTime = startingTime.plusSeconds(30);
                for (String request : this.requestMap.keySet()) {
                    for (LocalDateTime time : this.requestMap.get(request)) {
                        if (time.isBefore(intervalTime)
                                && (time.isAfter(startingTime) || time.isEqual(startingTime))) {
                            intervalRequests++;
                        }
                    }
                }
                intervalRequestsList.add(intervalRequests);
                startingTime = startingTime.plusSeconds(1);
            }
        }

        for (int intervalLoads : intervalRequestsList) {
            if (intervalLoads > maxLoad) {
                maxLoad = intervalLoads;
            }
        }

        return maxLoad;
    }

    /* Task 2 */

    /* Source: https://stackoverflow.com/questions/4738162/
    java-writing-reading-a-map-from-disk?
    fbclid=IwAR2k5WIuXOANDQXbHI56WU9wEb3wrR0_uCy6AWj9026Pzsn_D8GK1DLyEx0
    */

    /**
     * Writes this.timeMap to the localDirectory under the file name "timeMapFile"
     */
    public synchronized void writeStatsToFile() {
        try {
            FileOutputStream fos = new FileOutputStream(this.timeMapFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(this.timeMap);
            oos.writeObject(this.startTime);
            oos.close();

        } catch (IOException e) {
            System.out.println("Could not write to file");
        }

    }

    /**
     * Writes this.requestMap to the localDirectory under the file name "requestMapFile"
     */
    public synchronized void writeRequestsToFile() {
        try {
            FileOutputStream fos = new FileOutputStream(this.requestMapFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(this.requestMap);
            oos.close();

        } catch (IOException e) {
            System.out.println("Could not write to file");
        }

    }

    /**
     * Writes the start time of the wikiMediator to file
     */
    public synchronized void writeStartTimeToFile() {
        try {
            FileOutputStream fos = new FileOutputStream(this.startTimeFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(this.startTime);
            oos.close();

        } catch (IOException e) {
            System.out.println("Could not write to file");
        }

    }

    /**
     * Loads the start time of the wikiMediator
     */

    public synchronized void loadStartTimeFromFile() {
        try {
            FileInputStream fis = new FileInputStream(this.startTimeFile);
            ObjectInputStream ois = new ObjectInputStream(fis);
            this.startTime = (LocalDateTime) ois.readObject();
            ois.close();

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Could not load file");
        }

    }


    /**
     * Loads the requestMap from the localDirectory
     */
    public synchronized void loadRequestsFromFile() {
        try {
            FileInputStream fis = new FileInputStream(this.requestMapFile);
            ObjectInputStream ois = new ObjectInputStream(fis);
            this.requestMap = (Map) ois.readObject();
            ois.close();

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Could not load file");
        }

    }

    /**
     * Loads the timeMap from the localDirectory
     */
    public synchronized void loadStatsFromFile() {
        try {
            FileInputStream fis = new FileInputStream(this.timeMapFile);
            ObjectInputStream ois = new ObjectInputStream(fis);
            this.timeMap = (Map) ois.readObject();
            ois.close();

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Could not load file");
        }

    }


    /* Task 3 */

    /**
     * Finds a path through links from the startPage to the stopPage
     * @param startPage a page on en.wikipedia.org
     * @param stopPage a page on en.wikipedia.org
     * @return A list of strings on the path between the start Page and stop Page
     * Returns an empty list of strings if no such path exists or getPath exceeds 5 minutes
     * If start Page equals stop Page, returns a list of the single page
     */
    public List<String> getPath(String startPage, String stopPage) {
        synchronized (this) {
            List<LocalDateTime> requestDates = this.requestMap.get("getPath");
            requestDates.add(LocalDateTime.now());
            this.requestMap.replace("getPath", requestDates);
        }

        LocalDateTime startTime = LocalDateTime.now();
        Queue<String> queue = new LinkedBlockingQueue<>();
        Map<String, String> parentMap = new ConcurrentHashMap<>();
        parentMap.put(startPage, startPage);
        queue.add(startPage);
        boolean pageFound = false;

        if (startPage.equals(stopPage)) {
            List<String> returnList = new ArrayList<>();
            returnList.add(startPage);
            return returnList;
        }

        // if we reach the timeout value without finding the page, assume no possible path
        while (LocalDateTime.now().isBefore(startTime.plusMinutes(5)) && !pageFound) {
            String checkPage = queue.remove();
            List<String> linksOnPage = this.wiki.getLinksOnPage(checkPage);
            for (String page : linksOnPage) {
                // if parentMap doesn't contain page, we haven't visited it yet,
                // so we add it to the queue
                if (!parentMap.containsKey(page)) {
                    parentMap.put(page, checkPage);
                    queue.add(page);
                }

                // if page is the destination, break out of loop and set break flag in while loop
                // to true so we break out of queue loop
                if (page.equals(stopPage)) {
                    pageFound = true;
                    break;
                }

            }
        }

        // if we completely exhaust the queue, then stop Page is either invalid or an orphan page
        if (!pageFound) {
            return new ArrayList<>();
        } else {
            // we have a path from startPage to stopPage
            List<String> pathList = new ArrayList<>();
            pathList.add(stopPage);
            String parentPage = stopPage;

            while (!pathList.contains(startPage)) {
                pathList.add(parentMap.get(parentPage));
                parentPage = parentMap.get(parentPage);
            }

            List<String> pagePath = new ArrayList<>();
            for (int i = pathList.size() - 1; i >= 0; i--) {
                pagePath.add(pathList.get(i));

            }

            return pagePath;
        }
    }

    /**
     * Returns a list of pages that match a certain criteria
     * @param query a string that defines the search
     * @return a list of pages that match the criteria query
     * Returns an empty list if no such pages exist or query is invalid
     */
    public List<String> executeQuery(String query) {
        synchronized (this) {
            List<LocalDateTime> requestDates = this.requestMap.get("executeQuery");
            requestDates.add(LocalDateTime.now());
            this.requestMap.replace("executeQuery", requestDates);
        }

        List<String> queryList;

        try {
            queryList = this.parse(query);
        } catch (InvalidQueryException e) {
            System.out.println("Error parsing query.");
            return new ArrayList<>();
        }
        return queryList;
    }


    /**
     * Parse parses the client's string in order to find the query
     * @param query is not null
     * @return a list of strings that match the query in wikipedia
     * @throws InvalidQueryException if query is invalid according to grammar
     */
    public List<String> parse(String query) throws InvalidQueryException {
        CharStream stream = new ANTLRInputStream(query);
        QueryLexer lexer = new QueryLexer(stream);
        lexer.reportErrorsAsExceptions();
        TokenStream tokens = new CommonTokenStream(lexer);

        // Feed the tokens into the parser.
        QueryParser parser = new QueryParser(tokens);
        parser.reportErrorsAsExceptions();


        // Generate the parse tree using the starter rule.
        ParseTree tree = parser.query();

        ParseTreeWalker walker = new ParseTreeWalker();
        QueryListener_QueryCreator listener = new QueryListener_QueryCreator();
        walker.walk(listener, tree);

        synchronized (this) {
            List<String> queryList = listener.getQueries();
            return queryList;
        }

    }

    /**
     * Class that allows us to generate the correct query list
     */
    private class QueryListener_QueryCreator extends QueryBaseListener {
        boolean categoryFlag = false;
        boolean titleFlag = false;
        boolean authorFlag = false;
        boolean checkAuthors = false;
        boolean andFlag = false;
        boolean orFlag = false;
        String author = "";
        Stack<String> results = new Stack<>();
        List<String> seenList = new ArrayList<>();
        List<String> queryList = new ArrayList<>();
        Wiki wiki = new Wiki("en.wikipedia.org");

        /**
         * Based on the condition, adds the appropriate argument to the results stack
         * @param ctx the context tree that we walk thru, is not null
         * @modifies results, adds correct arguments to our stack
         */
        @Override
        public void exitSimpleCondition(QueryParser.SimpleConditionContext ctx) {
            if (ctx.CATEGORY() != null) {
                int length = ctx.STRING().getText().length();
                String category = "Category:" + ctx.STRING().getText().substring(1, length - 1);

                if (authorFlag) {
                    for (String c : wiki.getCategoryMembers(category)) {
                        String editor = wiki.getLastEditor(c);
                        if (!results.contains(editor)) {
                            results.push(editor);
                        }
                    }
                } else if (titleFlag) {
                    for (String t : wiki.getCategoryMembers(category)) {
                        results.push(t);
                    }
                } else {
                    for (String c : wiki.getCategoriesOnPage(category)) {
                        results.push(c);
                    }
                }
                results.push("");

            } else if (ctx.TITLE() != null) {
                int length = ctx.STRING().getText().length();
                String pageTitle = ctx.STRING().getText().substring(1, length - 1);

                if (authorFlag) {
                    results.push(wiki.getLastEditor(pageTitle));
                } else if (categoryFlag) {
                    for (String c : wiki.getCategoriesOnPage(pageTitle)) {
                        results.push(c);
                    }
                } else {
                    results.push(pageTitle);
                }
                results.push("");

            } else {
                int length = ctx.STRING().getText().length();
                author = ctx.STRING().getText().substring(1, length - 1);
                checkAuthors = true;
            }
        }

        /**
         * Sorts results based on the and condition or the or condition
         * @param ctx the context tree that we walk thru, is not null
         * @modifies seenList adds appropriate arguments to this list if seen
         * @modifies results takes off arguments from the stack
         * @modifies queryList adds appropriate arguments to this list if necessary
         */
        @Override
        public void exitCondition(QueryParser.ConditionContext ctx) {

            if (ctx.RPAREN() != null) {
                if (!results.isEmpty() && results.peek().equals("")) {
                    results.pop();
                }

                if (ctx.OR() != null) {
                    // if the previous condition was and, want to compare queries
                    if (andFlag) {
                        andFlag = false;
                        orFlag = true;
                        setCheckAuthors();


                    }  else if (orFlag) {
                        // if the previous condtion was or, want to add all queries
                        orFlag = false;
                        while (!results.isEmpty() && !results.peek().equals("")) {
                            queryList.add(results.pop());
                        }

                    } else {
                        // set the orFlag to true so we know we need to or everything
                        orFlag = true;
                    }
                } else {
                    if (orFlag) {
                        //if the previous condition was or, want to add all queries to seenList
                        orFlag = false;
                        andFlag = true;
                        if (!checkAuthors) {
                            while (!results.isEmpty() && !results.peek().equals("")) {
                                seenList.add(results.pop());
                            }
                            if (!results.isEmpty() && results.peek().equals("")) {
                                results.pop();
                            }
                        }

                    } else if (andFlag) {
                        // if the previous condition was and, want to compare queries
                        andFlag = false;
                        setCheckAuthors();

                    } else {
                        //set andFlag to true, add stuff to seen List
                        andFlag = true;
                        while (!results.isEmpty() && !results.peek().equals("")) {
                            if (!checkAuthors) {
                                seenList.add(results.pop());
                            } else {
                                seenList.add(author);
                                break;
                            }
                        }
                    }
                }
            }

        }

        /**
         * Sorts the query list
         * @param ctx the context tree that we walk thru, is not null
         * @modifies queryList adds appropriate arguments when necessary
         *           sorts the list if there is a sorted query
         */
        @Override public void exitQuery(QueryParser.QueryContext ctx) {

            if (orFlag) {
                while (!results.isEmpty()) {
                    if (!results.peek().equals("")) {
                        queryList.add(results.pop());
                    } else {
                        results.pop();
                    }
                }
            } else if (andFlag) {
                while (!results.isEmpty()) {
                    String checkQuery = results.pop();
                    if (!checkQuery.equals("")) {
                        if (checkAuthors) {
                            String editor = wiki.getLastEditor(checkQuery);
                            if (wiki.exists(checkQuery)
                                    && (seenList.contains(editor) || author.equals(editor))) {
                                queryList.add(checkQuery);
                            } else if (checkQuery.equals(author)) {
                                queryList.add(checkQuery);
                            }
                        } else {
                            if (seenList.contains(checkQuery)) {
                                queryList.add(checkQuery);
                            }
                        }
                    }
                }
            } else {
                while (!results.isEmpty()) {
                    if (!results.peek().equals("")) {
                        queryList.add(results.pop());
                    } else {
                        results.pop();
                    }
                }
            }


            if (ctx.SORTED() != null) {
                Collections.sort(queryList);
                if (ctx.SORTED().getText().equals("desc")) {
                    Collections.reverse(queryList);
                }
            }
        }

        /**
         * Determines the request the user is looking for
         * @param ctx the context tree that we walk thru, is not null
         */
        @Override
        public void enterQuery(QueryParser.QueryContext ctx) {
            if (ctx.ITEM().getText().equals("author")) {
                authorFlag = true;
            } else if (ctx.ITEM().getText().equals("page")) {
                titleFlag = true;
            } else {
                categoryFlag = true;
            }
        }

        /**
         *
         * @return queryList, a list of string that match the users query
         *                    returns an empty list if no suitable strings
         */
        public List<String> getQueries() {
            return queryList;
        }


        /**
         * helper method for the exitCondition method
         * @modifies results, takes appropriate arguments off the stack to sort
         * @modifies queryList, adds appropriate arguments if they fit the query
         * @modifies seenList, adds appropriate arguments if seen
         */
        private void setCheckAuthors() {
            while (!results.isEmpty() && !results.peek().equals("")) {
                String checkQuery = results.pop();
                if (checkAuthors) {
                    if (wiki.exists(checkQuery) && wiki.getLastEditor(checkQuery).equals(author)) {
                        queryList.add(checkQuery);
                    } else if (checkQuery.equals(author)) {
                        queryList.add(checkQuery);
                    }

                } else {
                    seenList.add(checkQuery);
                }
            }
            if (!results.isEmpty() && results.peek().equals("")) {
                results.pop();
            }
        }

    }

}
