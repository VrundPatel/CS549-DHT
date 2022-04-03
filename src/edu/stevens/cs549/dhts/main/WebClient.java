package edu.stevens.cs549.dhts.main;

import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import edu.stevens.cs549.dhts.resource.TableRow;
import org.glassfish.jersey.jackson.JacksonFeature;

import edu.stevens.cs549.dhts.activity.DHTBase;
import edu.stevens.cs549.dhts.activity.NodeInfo;
import edu.stevens.cs549.dhts.resource.TableRep;
import org.w3c.dom.Node;

public class WebClient {

    private static final String TAG = WebClient.class.getCanonicalName();

    private Logger logger = Logger.getLogger(TAG);

    private void error(String msg, Exception e) {
        logger.log(Level.SEVERE, msg, e);
    }

    /*
     * Creation of client instances is expensive, so just create one.
     */
    protected Client client;

    public WebClient() {
        client = ClientBuilder.newBuilder()
                .register(ObjectMapperProvider.class)
                .register(JacksonFeature.class)
                .build();
    }

    private void info(String mesg) {
        Log.weblog(TAG, mesg);
    }

    private Response getRequest(URI uri) {
        try {
            Response cr = client.target(uri)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .get();
            return cr;
        } catch (Exception e) {
            error("Exception during GET request", e);
            return null;
        }
    }

    private Response deleteRequest(URI uri) {
        try {
            Response cr = client.target(uri)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .delete();
            return cr;
        } catch (Exception e) {
            error("Exception during GET request", e);
            return null;
        }
    }

    private Response putRequest(URI uri, TableRep tableRep) {
        try {
            Response cr = client.target(uri)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .put(Entity.json(tableRep));
            return cr;
        } catch (Exception e) {
            error("Exception during PUT request", e);
            return null;
        }
    }

    /*
     * Ping a remote site to see if it is still available.
     */
    public boolean isFailed(URI base) {
        URI uri = UriBuilder.fromUri(base).path("info").build();
        Response c = getRequest(uri);
        return c.getStatus() >= 300;
    }

    /*
     * Get the predecessor pointer at a node.
     */
    public NodeInfo getPred(NodeInfo node) throws DHTBase.Failed {
        URI predPath = UriBuilder.fromUri(node.addr).path("pred").build();
        info("client getPred(" + predPath + ")");
        Response response = getRequest(predPath);
        if (response == null || response.getStatus() >= 300) {
            throw new DHTBase.Failed("GET /pred");
        } else {
            NodeInfo pred = response.readEntity(NodeInfo.class);
            return pred;
        }
    }

    /*
     * Notify node that we (think we) are its predecessor.
     */
    public TableRep notify(NodeInfo node, TableRep predDb) throws DHTBase.Failed {
        /*
         * The protocol here is more complex than for other operations. We
         * notify a new successor that we are its predecessor, and expect its
         * bindings as a result. But if it fails to accept us as its predecessor
         * (someone else has become intermediate predecessor since we found out
         * this node is our successor i.e. race condition that we don't try to
         * avoid because to do so is infeasible), it notifies us by returning
         * null. This is represented in HTTP by RC=304 (Not Modified).
         */
        NodeInfo thisNode = predDb.getInfo();
        UriBuilder ub = UriBuilder.fromUri(node.addr).path("notify");
        URI notifyPath = ub.queryParam("id", thisNode.id).build();
        info("client notify(" + notifyPath + ")");
        Response response = putRequest(notifyPath, predDb);
        if (response != null && response.getStatusInfo() == Response.Status.NOT_MODIFIED) {
            /*
             * Do nothing, the successor did not accept us as its predecessor.
             */
            return null;
        } else if (response == null || response.getStatus() >= 300) {
            throw new DHTBase.Failed("PUT /notify?id=ID");
        } else {
            TableRep bindings = response.readEntity(TableRep.class);
            return bindings;
        }
    }

    /*
     * Get bindings under a key.
     */
    public String[] get(NodeInfo n, String k) throws DHTBase.Failed {
        UriBuilder ub = UriBuilder.fromUri(n.addr).path("getValue");
        URI getPath = ub.queryParam("key", k).build();
        info("client getValue(" + getPath + ")");
        Response response = getRequest(getPath);
        if (response == null || response.getStatus() >= 300) {
            throw new DHTBase.Failed("GET /getValue?id=ID");
        } else {
            return response.readEntity(TableRow.class).vals;
        }
    }

    /*
     * Put bindings under a key.
     */
    public void add(NodeInfo n, String k, String v) throws DHTBase.Failed {
        UriBuilder ub = UriBuilder.fromUri(n.addr).path("add");
        URI addPath = ub.queryParam("key", k).queryParam("value", v).build();
        TableRep tablerep = new TableRep(null, null, 1);
        tablerep.entry[0] = new TableRow(k, new String[]{v});
        info("client add(" + addPath + ")");
        Response response = putRequest(addPath, tablerep);
        if (response == null || response.getStatus() >= 300) {
            throw new DHTBase.Failed("PUT /add?id=ID");
        }
    }

    /*
     * Delete bindings under a key.
     */
    public void delete(NodeInfo node, String key, String v) throws DHTBase.Failed {
        UriBuilder ub = UriBuilder.fromUri(node.addr).path("delete");
        URI deletePath = ub.queryParam("key", key).queryParam("value", v).build();
        info("client delete(" + deletePath + ")");
        Response response = deleteRequest(deletePath);
        if (response == null || response.getStatus() >= 300) {
            throw new DHTBase.Failed("DELETE /delete?id");
        }
    }

    /*
     * Find successor of an id. Used by join protocol
     */
    public NodeInfo findSuccessor(URI addr, int id) throws DHTBase.Failed {
        UriBuilder ub = UriBuilder.fromUri(addr).path("find");
        URI findPath = ub.queryParam("id", id).build();
        info("client findSuccessor(" + findPath + ")");
        Response response = getRequest(findPath);
        if (response == null || response.getStatus() >= 300) {
            throw new DHTBase.Failed("GET /find?id " + response.getStatus());
        } else {
            return response.readEntity(NodeInfo.class);
        }
    }

    public NodeInfo getSucc(NodeInfo node) throws DHTBase.Failed {
        URI succPath = UriBuilder.fromUri(node.addr).path("succ").build();
        info("client getSucc(" + succPath + ")");
        Response response = getRequest(succPath);
        if (response == null || response.getStatus() >= 300) {
            throw new DHTBase.Failed("GET /succ");
        } else {
            return response.readEntity(NodeInfo.class);
        }
    }

    public NodeInfo closestPrecedingFinger(NodeInfo node, int id) throws DHTBase.Failed {
        UriBuilder ub = UriBuilder.fromUri(node.addr).path("findClosestPrecedingFinger");
        URI getPath = ub.queryParam("id", id).build();
        info("client findClosestPrecedingFinger(" + getPath + ")");
        Response response = getRequest(getPath);
        if (response == null || response.getStatus() >= 300) {
            throw new DHTBase.Failed("GET /findClosestPrecedingFinger?id=ID");
        } else {
            return response.readEntity(NodeInfo.class);
        }
    }
}
