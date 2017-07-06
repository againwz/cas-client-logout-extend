/**
 * 
 */
package com.aldb.cas.logout.extend;

import javax.servlet.http.HttpSession;

import org.jasig.cas.client.session.SessionMappingStorage;

/**
 * @author sunff
 *
 */
public class ClusterSessionMappingStorage implements SessionMappingStorage {

    private static final String GLOBAL_NAMESPACE = "GLOBAL";
    public static final String ATTRIBUTES_KEY = "__attributes__";
    public static final String METADATA_KEY = "__metadata__";
    
    private String generate(String sessionId,String namespace,String name) {
        return "GlobalSession::" + sessionId + "::" + namespace + "::" + name;
    }
    
    
    public void addSessionById(String mappingId, HttpSession httpSession) {
        /**
         * 这里只需要存储mappingId与sessionId的关系,
         */
      //  httpSession.getId()
        
    }

    public void removeBySessionById(String sessionId) {
      
        /**
         *  直接应用 session从redis中删掉相应的session
         */
    }

    public HttpSession removeSessionByMappingId(String mappingId) {
       /**
        * 应用mappingId找到sessionId,然后应用sessionId删除相应的session属性
        */
        return null;
    }

}
