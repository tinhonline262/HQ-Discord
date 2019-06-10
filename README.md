# HQ-Discord
Just scrapes the HQ api and prints the question/puzzle data out to a discord channel

This is still a work in progress. Code style is kinda awful, this is mostly an exercise in teaching
myself about interacting with http clients and websockets, with a little React thrown in for good measure.


HOW TO IMPLEMENT
================

You need the following:

An HQ bearer token
<br>A Discord Bot token
<br>The channel IDs of any channels you want the bot to speak on.

Copy/paste the two tokens into their respective spots near the top of BotMain.java.
Then in main() copy paste your channel ID into its appropriate spot. If you would like
the bot to send messages to more than one channel, simply copy paste that entire line of code
and add a new channel ID to the Snowflake.of() call.

