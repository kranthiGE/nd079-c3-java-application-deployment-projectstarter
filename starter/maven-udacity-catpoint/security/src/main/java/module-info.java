module com.udacity.catpoint.security {
    requires transitive com.udacity.catpoint.image;
    requires miglayout;
    requires transitive java.desktop;
    requires java.prefs;
    requires com.google.common;
    requires com.google.gson;
    
    opens com.udacity.catpoint.security.data to com.google.gson;

}
