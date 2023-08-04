package com.timestored.qstudio.model;

import java.util.List;

import com.google.common.base.Objects;
import com.timestored.cstore.CAtomTypes;
import com.timestored.theme.Icon;

/**
 * A single q entity that exists on the server who's type/count etc is known.
 */
public interface ServerQEntity extends QEntity {

    public abstract String getName();

    public abstract CAtomTypes getType();

    /**
     * @return The count if known otherwise -1 if unknown
     */
    public abstract long getCount();

    public abstract boolean isTable();

    /**
     * @return Common q queries that could be sent for this element. (count/delete etc.)
     */
    public abstract List<QQuery> getQQueries();

    /**
     * Container for described queries
     */
    public static class QQuery {
        private final String query;
        private final String title;
        private final Icon icon;

        /**
         * @param icon Icon to show for this query or null if none desired.
         */
        public QQuery(String title, com.timestored.theme.Icon icon, String query) {
            this.query = query;
            this.title = title;
            this.icon = icon;
        }

        public String getTitle() {
            return title;
        }

        /**
         * @return Icon or null if none is set
         */
        public Icon getIcon() {
            return icon;
        }

        public String getQuery() {
            return query;
        }

        @Override
        public String toString() {
            return "QQuery [query=" + query + ", title=" + title + "]";
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof QQuery) {
                QQuery that = (QQuery) obj;
                return Objects.equal(this.query, that.query)
                        && Objects.equal(this.icon, that.icon)
                        && Objects.equal(this.title, that.title);
            }
            return false;
        }
    }
}
