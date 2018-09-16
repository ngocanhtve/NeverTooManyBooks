package com.eleybourn.bookcatalogue.database.definitions;

import com.eleybourn.bookcatalogue.database.DbSync;

/**
 * Class to store an index using a table name and a list of domain definitions.
 *
 * @author Philip Warner
 */
public class IndexDefinition {
    /** Full name of index */
    private final String mName;
    /** Table to which index applies */
    private final TableDefinition mTable;
    /** Domains in index */
    private final DomainDefinition[] mDomains;
    /** Flag indicating index is unique */
    private final boolean mIsUnique;

    /**
     * Constructor.
     *
     * @param name    name of index
     * @param unique  Flag indicating index is unique
     * @param table   Table to which index applies
     * @param domains Domains in index
     */
    IndexDefinition(String name, boolean unique, TableDefinition table, DomainDefinition... domains) {
        this.mName = name;
        this.mIsUnique = unique;
        this.mTable = table;
        this.mDomains = domains;
    }

    /**
     * @return UNIQUE flag.
     */
    public boolean getUnique() {
        return mIsUnique;
    }

    /**
     * @return list of domains in index.
     */
    public DomainDefinition[] getDomains() {
        return mDomains;
    }

    /**
     * Drop the index, if it exists.
     *
     * @param db Database to use.
     *
     * @return IndexDefinition (for chaining)
     */
    public IndexDefinition drop(DbSync.SynchronizedDb db) {
        db.execSQL("Drop Index If Exists " + mName);
        return this;
    }

    /**
     * Create the index.
     *
     * @param db Database to use.
     *
     * @return IndexDefinition (for chaining)
     */
    public IndexDefinition create(DbSync.SynchronizedDb db) {
        db.execSQL(this.getSql());
        return this;
    }

    /**
     * Return the SQL used to define the index.
     *
     * @return SQL Fragment
     */
    public String getSql() {
        StringBuilder sql = new StringBuilder("Create ");
        if (mIsUnique)
            sql.append(" Unique");
        sql.append(" Index ");
        sql.append(mName);
        sql.append(" on ").append(mTable.getName()).append("(\n");
        boolean first = true;
        for (DomainDefinition d : mDomains) {
            if (first) {
                first = false;
            } else {
                sql.append(",\n");
            }
            sql.append("    ");
            sql.append(d.name);
        }
        sql.append(")\n");
        return sql.toString();
    }
}
