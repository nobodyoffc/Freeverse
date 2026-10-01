package db.fcdsl;

/**
 * Value type of a queryable field. It decides how FCDSL values are read
 * (a range on a LONG compares numbers, on a KEYWORD compares strings) and how
 * the field is encoded in index keys.
 */
public enum FieldType {
    LONG,
    DOUBLE,
    BOOLEAN,
    /** An exact string, like an ES keyword. */
    KEYWORD,
    /** A string that {@code match} splits into tokens. Other operators treat it as a KEYWORD. */
    TEXT
}
