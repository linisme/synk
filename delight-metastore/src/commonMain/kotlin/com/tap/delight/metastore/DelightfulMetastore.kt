package com.tap.delight.metastore

import com.tap.delight.metastore.cache.MemCache
import com.tap.delight.metastore.hash.Hasher
import com.tap.synk.encode.decodeToHashmap
import com.tap.synk.encode.encodeToString
import com.tap.synk.meta.store.MetaStore

class DelightfulMetastore internal constructor(
    private val database: DelightfulDatabase,
    private val namespace: String,
    private val hasher: Hasher,
    private val cache: MemCache<String, String>
) : MetaStore {

    private fun getMetaFromDatabase(id: String, namespace: String): HashMap<String, String>? {
        return database.multistoreQueries.getById(id, namespace).executeAsOneOrNull()?.data_?.decodeToHashmap()
    }

    private fun getMetaFromCache(id: String, namespace: String): HashMap<String, String>? {
        val key = hasher.hash("$namespace:$id")
        return cache[key]?.decodeToHashmap()
    }

    private fun putMetaInDatabase(id: String, namespace: String, data: String) {
        return database.multistoreQueries.upsert(id, namespace, data)
    }

    private fun putMetaInCache(id: String, namespace: String, data: String) {
        val key = hasher.hash("$namespace:$id")
        cache.put(key, data)
    }

    override fun warm() {
        val results = database.multistoreQueries.allForNamespace(namespace, cache.maxSize().toLong()).executeAsList()
        results.forEach { multistore ->
            putMetaInCache(multistore.id, multistore.namespace, multistore.data_)
        }
    }

    override fun getMeta(id: String): HashMap<String, String>? {
        return getMetaFromCache(id, namespace) ?: getMetaFromDatabase(id, namespace)
    }

    override fun putMeta(id: String, meta: HashMap<String, String>) {
        val data = meta.encodeToString()

        putMetaInDatabase(id, namespace, data)
        putMetaInCache(id, namespace, data)
    }
}
