# AndroidICB
Android Client for [ICB (Internet Citizen's Band)](http://www.icb.net) chat.

This is a really basic client. It's still a work in progress.

When you launch it, you need to choose the Preferences item from the menu and set your nick, group, and (optionally) a password.
After that, just choose the "Connect" item from the menu to connect. To disconnect, choose the same menu item - it should have changed
to read "Disconnect".

This runs as a single-instance on your Android. You can background it and it will stay connected, and you can bring it back to the front any time. If you try to launch a second instance, it'll just bring your current instance to the front.

The minimum requirement for running is Android 4.4.

I currently (as of 11/14/2017) use AndroidStudio 3.0 and Gradle 4.1 to build. It should build just fine on early versions of AndroidStudio and Gradle, but you may need to add *buildToolsVersion '25.0.0'* or similar to the app/build.gradle file.

