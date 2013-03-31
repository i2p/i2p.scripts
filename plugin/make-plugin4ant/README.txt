This is makeplugin.sh as an Ant build script. This script has been tested in
Linux and Windows but it should work with any OS that can run Ant.

If your plugin is built with Ant, you can integrate this script into your project by copying the file
"make-plugin.xml" to your plugin's source directory. Add the following to your build.xml:
     <import file="make-plugin.xml" />


Then instead of using
    <exec executable="scripts/makeplugin.sh" failonerror="true">
        <arg value="plugin" />
    </exec>

...you'd use the following instead.:
     <ant target="make-plugin" />


If you'd don't want to import make-plugin.xml you can call it in the following way:
    <ant antfile="make-plugin.xml" target="make-plugin" />

