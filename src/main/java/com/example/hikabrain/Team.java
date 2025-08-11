package com.example.hikabrain; public enum Team { RED, BLUE, SPECTATOR; public Team other(){ return this==RED?BLUE:(this==BLUE?RED:SPECTATOR);} }
