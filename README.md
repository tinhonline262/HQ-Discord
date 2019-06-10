# HQ-Discord
Just scrapes the HQ api and prints the question/puzzle data out to a discord channel

This is still a work in progress. Need to implement verification on questions/answers
to ensure that the bot doesn't double print anything. Also plan on implementing projected payout
for words and non-jackpot trivia games. Hopefully I'll eventually be able to find a pattern
in how checkpoint payouts work and implement that, but that isn't a major concern for me right now.


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

