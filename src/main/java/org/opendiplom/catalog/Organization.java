package org.opendiplom.catalog;

import java.util.ArrayList;
import java.util.List;

/**
 * The organization as its diplomas print it: the full name, the locality
 * after it (D-03) and the head, printed as the surname with initials.
 */
public final class Organization {
    /** Its lines as printed. */
    public final String fullName;
    /** «г. Йошкар-Ола». */
    public final String locality;
    public final String headLastName;
    public final String headFirstName;
    public final String headMiddleName;

    public Organization(
        final String fullName, final String locality, final String headLastName, final String headFirstName,
        final String headMiddleName
    ) {
        this.fullName = text(fullName);
        this.locality = text(locality);
        this.headLastName = text(headLastName);
        this.headFirstName = text(headFirstName);
        this.headMiddleName = text(headMiddleName);
    }

    /** None filled in yet. */
    public static Organization empty() {
        return new Organization("", "", "", "", "");
    }

    /** What the documents need and is not filled in; empty when nothing. */
    public List<String> missing() {
        final List<String> missing = new ArrayList<>();
        if (this.fullName.isEmpty()) {
            missing.add("полное наименование");
        }
        if (this.locality.isEmpty()) {
            missing.add("населённый пункт");
        }
        if (this.headLastName.isEmpty() || this.headFirstName.isEmpty()) {
            missing.add("фамилия и имя руководителя");
        }
        return missing;
    }

    /** «А.А. Иванов», as the head signs. */
    public String head() {
        return (initial(this.headFirstName) + initial(this.headMiddleName) + " " + this.headLastName).strip();
    }

    private static String initial(final String name) {
        return name.isEmpty() ? "" : name.substring(0, 1) + ".";
    }

    private static String text(final String value) {
        return value == null ? "" : value.strip();
    }
}
