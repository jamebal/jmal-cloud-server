package com.jmal.clouddisk.config.mongodb;

import com.mongodb.*;
import com.mongodb.client.*;
import com.mongodb.client.model.bulk.ClientBulkWriteOptions;
import com.mongodb.client.model.bulk.ClientBulkWriteResult;
import com.mongodb.client.model.bulk.ClientNamespacedWriteModel;
import com.mongodb.connection.ClusterDescription;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class NoOpMongoClient implements MongoClient {
    @Override
    public void close() {

    }

    @Override
    public ClusterDescription getClusterDescription() {
        return null;
    }

    @Override
    public CodecRegistry getCodecRegistry() {
        return null;
    }

    @Override
    public ReadPreference getReadPreference() {
        return null;
    }

    @Override
    public WriteConcern getWriteConcern() {
        return null;
    }

    @Override
    public ReadConcern getReadConcern() {
        return null;
    }

    @Override
    public Long getTimeout(TimeUnit timeUnit) {
        return 0L;
    }

    @Override
    public MongoCluster withCodecRegistry(CodecRegistry codecRegistry) {
        return null;
    }

    @Override
    public MongoCluster withReadPreference(ReadPreference readPreference) {
        return null;
    }

    @Override
    public MongoCluster withWriteConcern(WriteConcern writeConcern) {
        return null;
    }

    @Override
    public MongoCluster withReadConcern(ReadConcern readConcern) {
        return null;
    }

    @Override
    public MongoCluster withTimeout(long l, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public MongoDatabase getDatabase(String s) {
        return null;
    }

    @Override
    public ClientSession startSession() {
        return null;
    }

    @Override
    public ClientSession startSession(ClientSessionOptions clientSessionOptions) {
        return null;
    }

    @Override
    public MongoIterable<String> listDatabaseNames() {
        return null;
    }

    @Override
    public MongoIterable<String> listDatabaseNames(ClientSession clientSession) {
        return null;
    }

    @Override
    public ListDatabasesIterable<Document> listDatabases() {
        return null;
    }

    @Override
    public ListDatabasesIterable<Document> listDatabases(ClientSession clientSession) {
        return null;
    }

    @Override
    public <TResult> ListDatabasesIterable<TResult> listDatabases(Class<TResult> aClass) {
        return null;
    }

    @Override
    public <TResult> ListDatabasesIterable<TResult> listDatabases(ClientSession clientSession, Class<TResult> aClass) {
        return null;
    }

    @Override
    public ChangeStreamIterable<Document> watch() {
        return null;
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(Class<TResult> aClass) {
        return null;
    }

    @Override
    public ChangeStreamIterable<Document> watch(List<? extends Bson> list) {
        return null;
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(List<? extends Bson> list, Class<TResult> aClass) {
        return null;
    }

    @Override
    public ChangeStreamIterable<Document> watch(ClientSession clientSession) {
        return null;
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(ClientSession clientSession, Class<TResult> aClass) {
        return null;
    }

    @Override
    public ChangeStreamIterable<Document> watch(ClientSession clientSession, List<? extends Bson> list) {
        return null;
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(ClientSession clientSession, List<? extends Bson> list, Class<TResult> aClass) {
        return null;
    }

    @Override
    public ClientBulkWriteResult bulkWrite(List<? extends ClientNamespacedWriteModel> list) throws ClientBulkWriteException {
        return null;
    }

    @Override
    public ClientBulkWriteResult bulkWrite(List<? extends ClientNamespacedWriteModel> list, ClientBulkWriteOptions clientBulkWriteOptions) throws ClientBulkWriteException {
        return null;
    }

    @Override
    public ClientBulkWriteResult bulkWrite(ClientSession clientSession, List<? extends ClientNamespacedWriteModel> list) throws ClientBulkWriteException {
        return null;
    }

    @Override
    public ClientBulkWriteResult bulkWrite(ClientSession clientSession, List<? extends ClientNamespacedWriteModel> list, ClientBulkWriteOptions clientBulkWriteOptions) throws ClientBulkWriteException {
        return null;
    }
}
