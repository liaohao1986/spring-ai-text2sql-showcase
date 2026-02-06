package com.example.text2sql.service;

/** 
 * Text2SqlService
 *
 * @author liaoh
 * @date 2026/02/05 16:37 
 */ 
public interface Text2SqlService {
    Text2SqlResult processQuery(String userQuery);
    
    Text2SqlResult processQueryWithTableNames(String userQuery, String tableNames);
}
