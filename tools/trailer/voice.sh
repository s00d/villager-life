#!/bin/zsh
# voice.sh en|ru : the dev client's voice clips (build resources voice/en) become that language; then /vnaprec lang
B=~/Downloads/FireShot/villager-news-ru-port/neoforge/versions/1.21.1/build/resources/main/assets/villager_life/sounds/voice
S=~/Downloads/FireShot/villager-news-ru-port/neoforge/src/main/resources/assets/villager_life/sounds/voice
# always: a restarted client copies the English clips back over the folder and leaves the old marker in it
rm -rf $B/en.next && cp -c -R $S/$1 $B/en.next && echo $1 > $B/en.next/.lang && rm -rf $B/en && mv $B/en.next $B/en
echo voice $1
