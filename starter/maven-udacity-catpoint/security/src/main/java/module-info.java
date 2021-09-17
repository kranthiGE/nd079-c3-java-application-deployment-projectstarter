module com.udacity.catpoint.security {
    exports com.udacity.catpoint.security.application;
    exports com.udacity.catpoint.security.data;
    exports com.udacity.catpoint.security.service;
    
    requires transitive com.udacity.catpoint.image;
    requires miglayout;
    requires transitive java.desktop;
    requires java.prefs;
    requires com.google.common;
    requires com.google.gson;
    
    opens com.udacity.catpoint.security.data to com.google.gson;

}
