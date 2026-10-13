# Pausensprüche (Quelle der Ressourcen)

Stand: 2026-10-13. Der Pausenbildschirm (`ZenPauseActivity`) zeigt unter dem Countdown einen dieser Sprüche, je Pause einen
anderen, ohne Wiederholung, bis alle durch sind (Regeln: `rules.md`, Abschnitt „Pflichtpause“; Umsetzung: `spec-pause-nach-abbruch.md`).
Diese Datei ist die **einzige Quelle**: `python3 zen-docs/gen_sprueche.py` erzeugt daraus
`app/src/main/res/values/zen_quotes.xml` (Englisch) und `app/src/main/res/values-de/zen_quotes.xml` (Deutsch).
Beim Ändern bitte nur die Tabellen bearbeiten und das Skript laufen lassen. Keine senkrechten Striche im Text.

## Quellenregel
- **Zitate nur, wenn belegbar und gemeinfrei.** Autoren, die seit über 70 Jahren tot sind (Marc Aurel, Seneca, Epiktet,
  Laozi, Bashō, Ryōkan, Linji, Montaigne, Pascal, Thoreau, Rilke) und klassische Zen-Sammlungen, jeweils mit Stelle.
- **Übersetzungen:** Englisch wörtlich nur aus gemeinfreien Übersetzungen, die vor über 70 Jahren verstorbene Übersetzer
  geschaffen haben (George Long, Marc Aurel 1862 und Epiktet 1877; James Legge, Laozi 1891; Thoreau im Original). Alles andere
  ist eine **eigene Übertragung** (Deutsch durchgehend, Englisch bei Seneca, Bashō, Ryōkan, Linji, Montaigne, Pascal, Rilke,
  Laozi 12/44). Moderne Übersetzungen (z. B. Gummere/Basore für Seneca, Watts/Suzuki) sind nicht gemeinfrei und wurden nicht verwendet.
- **Kennzeichnung in der App:** wörtliche Zitate „— Autor, Werk Stelle“; eigene, sinngemäße Wiedergaben „— nach Autor, Werk Stelle“.
- **Ausgeschlossen:** zweifelhafte Internet-Zitate (u. a. die typischen falschen „Buddha“-, „Laozi“- oder „Marc Aurel“-Sprüche),
  alles ohne auffindbare Stelle. Im Zweifel weggelassen (z. B. Issa „Tauwelt“ wegen des Trauer-Kontexts, Seneca „De brevitate
  vitae 1“ als zu mahnend). Der frühere Q27 „Still sitzen, nichts tun. Der Frühling kommt, und das Gras wächst
  von selbst.“ wurde gestrichen: die Zuordnung zum Zenrin kushū ist nicht belegbar (Vers nur aus der englischsprachigen
  Sekundärliteratur bekannt, keine auffindbare Stelle).
- **Ton** der eigenen Sätze: ruhig, Du-Form, ohne Vorwurf und ohne Belehrung; Einladungen statt Aufforderungen.

Spalten: `Nr` (Q = Zitat, E = eigener Satz, A = Atemhinweis), Text Deutsch, Text Englisch, Anzeige DE / Anzeige EN (Zeile unter
dem Spruch, leer = anonym), Beleg.

## Zitate (gemeinfrei, mit Stelle)

| Nr | Deutsch | English | Anzeige DE | Anzeige EN | Beleg |
|---|---|---|---|---|---|
| Q01 | Nirgends zieht man sich ruhiger und ungestörter zurück als in die eigene Seele. | For nowhere either with more quiet or more freedom from trouble does a man retire than into his own soul. | nach Marc Aurel, Selbstbetrachtungen IV 3 | Marcus Aurelius, Meditations IV.3 | EN wörtlich George Long (1862); DE eigene Übertragung |
| Q02 | Beschäftige dich mit wenigem, wenn du ruhig sein willst. | Occupy thyself with few things, says the philosopher, if thou wouldst be tranquil. | nach Marc Aurel, Selbstbetrachtungen IV 24 | Marcus Aurelius, Meditations IV.24 | EN wörtlich Long; Marc Aurel zitiert Demokrit (Fragment B 3); DE eigene Übertragung |
| Q03 | Wenn dich etwas Äußeres bedrückt, ist es nicht die Sache selbst, die dich stört, sondern dein Urteil darüber. | If thou art pained by any external thing, it is not this thing that disturbs thee, but thy own judgement about it. | nach Marc Aurel, Selbstbetrachtungen VIII 47 | Marcus Aurelius, Meditations VIII.47 | EN wörtlich Long; DE eigene Übertragung |
| Q04 | Lass dich vom Kommenden nicht beunruhigen. Du wirst ihm, wenn nötig, mit derselben Vernunft begegnen, die dir jetzt hilft. | Let not future things disturb thee, for thou wilt come to them, if it shall be necessary, having with thee the same reason which now thou usest for present things. | nach Marc Aurel, Selbstbetrachtungen VII 8 | Marcus Aurelius, Meditations VII.8 | EN wörtlich Long; DE eigene Übertragung |
| Q05 | Wie deine gewohnten Gedanken sind, so wird dein Geist; die Seele nimmt die Farbe der Gedanken an. | Such as are thy habitual thoughts, such also will be the character of thy mind; for the soul is dyed by the thoughts. | nach Marc Aurel, Selbstbetrachtungen V 16 | Marcus Aurelius, Meditations V.16 | EN wörtlich Long; DE eigene Übertragung |
| Q06 | Schau nach innen. Dort ist die Quelle des Guten, und sie sprudelt immer wieder, wenn du nur immer wieder gräbst. | Look within. Within is the fountain of good, and it will ever bubble up, if thou wilt ever dig. | nach Marc Aurel, Selbstbetrachtungen VII 59 | Marcus Aurelius, Meditations VII.59 | EN wörtlich Long; DE eigene Übertragung |
| Q07 | Nur die Gegenwart kann dir genommen werden, denn nur sie hast du wirklich. | The present is the only thing of which a man can be deprived, if it is true that this is the only thing which he has. | nach Marc Aurel, Selbstbetrachtungen II 14 | Marcus Aurelius, Meditations II.14 | EN wörtlich Long (Satzanfang „For“ weggelassen); DE eigene Übertragung |
| Q08 | Gehöre dir selbst. | Claim yourself for yourself. | nach Seneca, Briefe an Lucilius 1 | after Seneca, Letters to Lucilius 1 | Ep. 1,1 „Vindica te tibi“; beide eigene Übertragung |
| Q09 | Während man aufschiebt, eilt das Leben vorbei. | While we put things off, life runs past. | nach Seneca, Briefe an Lucilius 1 | after Seneca, Letters to Lucilius 1 | Ep. 1,3 „Dum differtur vita transcurrit“; beide eigene Übertragung |
| Q10 | Das erste Zeichen eines geordneten Geistes: dass er verweilen und bei sich selbst bleiben kann. | The first sign of a settled mind is that it can stay in one place and linger with itself. | nach Seneca, Briefe an Lucilius 2 | after Seneca, Letters to Lucilius 2 | Ep. 2,1 „Primum argumentum compositae mentis existimo posse consistere et secum morari“; beide eigene Übertragung |
| Q11 | Wer überall ist, ist nirgends. | Whoever is everywhere is nowhere. | nach Seneca, Briefe an Lucilius 2 | after Seneca, Letters to Lucilius 2 | Ep. 2,2 „Nusquam est qui ubique est“; beide eigene Übertragung |
| Q12 | Wir leiden öfter in der Vorstellung als in der Wirklichkeit. | We suffer more often in imagination than in reality. | nach Seneca, Briefe an Lucilius 13 | after Seneca, Letters to Lucilius 13 | Ep. 13,4 „saepius opinione quam re laboramus“; beide eigene Übertragung |
| Q13 | Nicht den Ort musst du wechseln, sondern deinen Sinn. | It is your mind you need to change, not the sky above you. | nach Seneca, Briefe an Lucilius 28 | after Seneca, Letters to Lucilius 28 | Ep. 28,1 „animum debes mutare, non caelum“; beide eigene Übertragung |
| Q14 | Was nützt die Stille ringsum, wenn es innen tost? | What good is silence all around if the feelings within are roaring? | nach Seneca, Briefe an Lucilius 56 | after Seneca, Letters to Lucilius 56 | Ep. 56,5 „quid prodest totius regionis silentium, si adfectus fremunt?“; beide eigene Übertragung |
| Q15 | Nicht die Dinge beunruhigen die Menschen, sondern ihre Meinungen über die Dinge. | Men are disturbed not by the things which happen, but by the opinions about the things. | nach Epiktet, Handbüchlein 5 | Epictetus, Enchiridion 5 | EN wörtlich George Long (1877); DE eigene Übertragung |
| Q16 | Verlange nicht, dass die Dinge geschehen, wie du willst, sondern wünsche sie, wie sie geschehen, und dein Leben wird ruhig fließen. | Seek not that the things which happen should happen as you wish; but wish the things which happen to be as they are, and you will have a tranquil flow of life. | nach Epiktet, Handbüchlein 8 | Epictetus, Enchiridion 8 | EN wörtlich Long; DE eigene Übertragung |
| Q17 | Manches liegt in unserer Macht, anderes nicht. | Of things some are in our power, and others are not. | nach Epiktet, Handbüchlein 1 | Epictetus, Enchiridion 1 | EN wörtlich Long; DE eigene Übertragung |
| Q18 | Wer kann trübes Wasser klären? Lass es ruhen, dann wird es nach und nach klar. | Who can make the muddy water clear? Let it be still, and it will gradually become clear. | nach Laozi, Daodejing 15 | Laozi, Tao Te Ching 15 | EN wörtlich James Legge (1891), Klammern Legges weggelassen; DE eigene Übertragung |
| Q19 | Eine Reise von tausend Meilen beginnt mit einem einzigen Schritt. | The journey of a thousand li commenced with a single step. | nach Laozi, Daodejing 64 | Laozi, Tao Te Ching 64 | EN wörtlich Legge; DE eigene Übertragung |
| Q20 | Dreißig Speichen treffen sich in der Nabe; doch erst der leere Raum macht das Rad brauchbar. | Thirty spokes unite in the one nave; but it is on the empty space that the use of the wheel depends. | nach Laozi, Daodejing 11 | Laozi, Tao Te Ching 11 | EN wörtlich Legge, Klammer „(for the axle)“ weggelassen; DE eigene Übertragung |
| Q21 | Die fünf Farben machen das Auge blind, die fünf Töne das Ohr taub. | The five colours blind the eye; the five notes deafen the ear. | nach Laozi, Daodejing 12 | after Laozi, Tao Te Ching 12 | Kap. 12 Anfang; beide eigene Übertragung (Legges Fassung ist gereimt) |
| Q22 | Wer weiß, wann es genug ist, gerät nicht in Gefahr. | Who knows when to stop is free from danger. | nach Laozi, Daodejing 44 | after Laozi, Tao Te Ching 44 | Kap. 44 „知止不殆“; beide eigene Übertragung |
| Q23 | Wer andere kennt, ist klug; wer sich selbst kennt, ist weise. | He who knows other men is discerning; he who knows himself is intelligent. | nach Laozi, Daodejing 33 | Laozi, Tao Te Ching 33 | EN wörtlich Legge; DE eigene Übertragung |
| Q24 | Der alte Teich. Ein Frosch springt hinein, das Geräusch des Wassers. | The old pond. A frog jumps in, the sound of water. | nach Bashō (1686) | after Bashō (1686) | Haiku „furuike ya kawazu tobikomu mizu no oto“; beide eigene Übertragung |
| Q25 | Stille. Die Stimme der Zikaden dringt in die Felsen. | Stillness. The cicadas' voices seep into the rocks. | nach Bashō, Auf schmalen Pfaden durchs Hinterland | after Bashō, The Narrow Road to the Deep North | Haiku „shizukasa ya iwa ni shimiiru semi no koe“ (Ryūshaku-ji, 1689); beide eigene Übertragung |
| Q26 | Der Dieb ließ es zurück: den Mond im Fenster. | The thief left it behind: the moon in the window. | nach Ryōkan | after Ryōkan | Haiku „nusubito ni torinokosareshi mado no tsuki“; beide eigene Übertragung |
| Q27 | Zieh dich an, iss dein Essen, und wenn du müde bist, leg dich hin. | Put on your clothes, eat your meal, and when you are tired, lie down. | nach Linji (Linji lu) | after Linji (Record of Linji) | Linji lu „著衣喫飯，困來即臥“; beide eigene Übertragung |
| Q28 | Wenn ich tanze, tanze ich; wenn ich schlafe, schlafe ich. | When I dance, I dance; when I sleep, I sleep. | nach Montaigne, Essais III 13 | after Montaigne, Essays III.13 | „Quand je danse, je danse; quand je dors, je dors“ (De l'expérience); beide eigene Übertragung |
| Q29 | Alles Unglück der Menschen kommt daher, dass sie nicht ruhig in einem Zimmer bleiben können. | All of our unhappiness comes from one thing: not knowing how to stay quietly in a room. | nach Pascal, Gedanken 139 | after Pascal, Pensées 139 | Pensées Nr. 139 Brunschvicg (136 Lafuma) „tout le malheur des hommes vient d'une seule chose, qui est de ne savoir pas demeurer en repos dans une chambre“; beide eigene Übertragung |
| Q30 | Unser Leben verzettelt sich in Einzelheiten. Vereinfache, vereinfache. | Our life is frittered away by detail. … Simplify, simplify. | nach Thoreau, Walden | Thoreau, Walden | Kap. 2 „Where I Lived, and What I Lived For“ (1854), EN wörtlich mit Auslassung; DE eigene Übertragung |
| Q31 | Die Zeit ist nur der Bach, in dem ich fischen gehe. | Time is but the stream I go a-fishing in. | nach Thoreau, Walden | Thoreau, Walden | Kap. 2 (1854), EN wörtlich; DE eigene Übertragung |
| Q32 | … Geduld zu haben gegen alles Ungelöste in Ihrem Herzen und zu versuchen, die Fragen selbst liebzuhaben. | Be patient toward all that is unsolved in your heart, and try to love the questions themselves. | Rilke, Briefe an einen jungen Dichter | after Rilke, Letters to a Young Poet | 4. Brief (16. Juli 1903), DE wörtlich; EN eigene Übertragung |

## Eigene Sätze (anonym)

| Nr | Deutsch | English | Anzeige DE | Anzeige EN | Beleg |
|---|---|---|---|---|---|
| E01 | Atme einmal tief durch. Das Video läuft nicht weg. | Take one deep breath. The video isn't going anywhere. | | | eigen |
| E02 | Schau für einen Moment aus dem Fenster. | Look out of the window for a moment. | | | eigen |
| E03 | Spür deine Füße auf dem Boden. | Feel your feet on the ground. | | | eigen |
| E04 | Nichts muss jetzt passieren. | Nothing needs to happen right now. | | | eigen |
| E05 | Lass die Schultern sinken. | Let your shoulders drop. | | | eigen |
| E06 | Vielleicht ist jetzt ein Glas Wasser schön. | Maybe a glass of water would be nice now. | | | eigen |
| E07 | Eine Pause ist auch ein Teil des Tages. | A pause is part of the day, too. | | | eigen |
| E08 | Was hörst du gerade, wenn du genau hinhörst? | What can you hear right now, if you listen closely? | | | eigen |
| E09 | Lass die Augen auf etwas in der Ferne ruhen. | Let your eyes rest on something far away. | | | eigen |
| E10 | Steh kurz auf und streck dich, wenn du magst. | Stand up and stretch for a moment, if you like. | | | eigen |
| E11 | Du verpasst gerade nichts. | You aren't missing anything right now. | | | eigen |
| E12 | Die Stille gehört auch zum Programm. | Silence is part of the programme, too. | | | eigen |
| E13 | Wie geht es dir gerade, ganz ehrlich? | How are you right now, honestly? | | | eigen |
| E14 | Einen Moment lang nichts. Das reicht. | A moment of nothing. That's enough. | | | eigen |
| E15 | Dein Kopf darf auch mal leer laufen. | Your mind is allowed to idle for a while. | | | eigen |
| E16 | Aus Langeweile wird oft eine gute Idee. | Boredom is often where a good idea begins. | | | eigen |
| E17 | Lass das Gesehene ein wenig nachklingen. | Let what you have seen settle for a while. | | | eigen |
| E18 | Was war heute das Schönste, das du gesehen hast, abseits jedes Bildschirms? | What was the nicest thing you saw today, away from any screen? | | | eigen |
| E19 | Leg das Handy für einen Moment mit dem Display nach unten. | Put your phone face down for a moment. | | | eigen |
| E20 | Bemerke, wie die Luft ein- und ausströmt. | Notice the air flowing in and out. | | | eigen |
| E21 | Lass Stirn und Kiefer weich werden. | Let your forehead and jaw soften. | | | eigen |
| E22 | Die Welt draußen hat ihr eigenes Tempo. | The world outside moves at its own pace. | | | eigen |
| E23 | Du darfst jetzt einfach hier sein. | You can simply be here now. | | | eigen |
| E24 | Gibt es jemanden, dem du gern kurz schreiben würdest? | Is there someone you'd like to send a short message to? | | | eigen |
| E25 | Die nächsten Minuten gehören dir. | The next few minutes are yours. | | | eigen |
| E26 | Gönn deinen Augen etwas Grünes. | Give your eyes something green to look at. | | | eigen |
| E27 | Wie fühlt sich dein Körper gerade an? | How does your body feel right now? | | | eigen |
| E28 | Ein ruhiger Moment ist kein verlorener Moment. | A quiet moment is not a lost moment. | | | eigen |
| E29 | Was möchtest du als Nächstes wirklich tun? | What would you really like to do next? | | | eigen |
| E30 | Zähl fünf Dinge, die du gerade sehen kannst. | Name five things you can see right now. | | | eigen |
| E31 | Geh ein paar Schritte, wenn dir danach ist. | Walk a few steps, if you feel like it. | | | eigen |
| E32 | Ein kurzer Blick in den Himmel genügt. | A quick look at the sky is enough. | | | eigen |
| E33 | Spür, wie du gerade sitzt oder liegst. | Notice how you are sitting or lying right now. | | | eigen |
| E34 | Gedanken kommen und gehen. Du musst keinem folgen. | Thoughts come and go. You don't have to follow any of them. | | | eigen |
| E35 | Hier gibt es gerade nichts zu tun. | There is nothing to do here right now. | | | eigen |
| E36 | Die Pause ist kein Hindernis, sondern ein Raum. | The pause is not an obstacle. It is a space. | | | eigen |
| E37 | Vielleicht tut ein offenes Fenster gerade gut. | Maybe an open window would feel good right now. | | | eigen |
| E38 | Was riechst du gerade? | What can you smell right now? | | | eigen |
| E39 | Das Gute wartet auch nach der Pause noch auf dich. | The good things will still be there after the pause. | | | eigen |
| E40 | Reib die Hände aneinander, bis sie warm sind. | Rub your hands together until they are warm. | | | eigen |
| E41 | Ruhe braucht keinen Grund. | Rest needs no reason. | | | eigen |
| E42 | Du bist mehr als das, was du gerade anschaust. | You are more than whatever you are watching. | | | eigen |
| E43 | Kein Fortschrittsbalken, keine Eile. | No progress bar, no hurry. | | | eigen |
| E44 | Lass den Blick weich werden. | Let your gaze soften. | | | eigen |
| E45 | Lausch einem Geräusch, das weit weg ist. | Listen for a sound that is far away. | | | eigen |
| E46 | Ein Moment ohne Bildschirm ist ein Moment für dich. | A moment without a screen is a moment for you. | | | eigen |
| E47 | Vielleicht ist jetzt Zeit für einen Tee. | Maybe it's time for a cup of tea. | | | eigen |
| E48 | Welchen Gedanken aus dem Video möchtest du behalten? | Which thought from the video would you like to keep? | | | eigen |
| E49 | Auch Wolken haben keine Eile. | Clouds are in no hurry either. | | | eigen |
| E50 | Die Pause hat begonnen. Mehr ist gerade nicht zu tun. | The pause has begun. There is nothing more to do for now. | | | eigen |
| E51 | Wie wären drei langsame Schulterkreise? | How about three slow shoulder rolls? | | | eigen |
| E52 | Was liegt heute noch Schönes vor dir? | What good thing is still ahead of you today? | | | eigen |
| E53 | Der Moment zwischen zwei Dingen ist auch ein Moment. | The moment between two things is a moment, too. | | | eigen |
| E54 | Schließ für ein paar Atemzüge die Augen. | Close your eyes for a few breaths. | | | eigen |
| E55 | Deine Augen dürfen sich jetzt ausruhen. | Your eyes may rest now. | | | eigen |
| E56 | Nicht alles muss heute noch gesehen werden. | Not everything needs to be seen today. | | | eigen |
| E57 | Gerade ist ein guter Moment, um nichts zu wollen. | This is a good moment to want nothing. | | | eigen |

## Atemhinweise (anonym)

| Nr | Deutsch | English | Anzeige DE | Anzeige EN | Beleg |
|---|---|---|---|---|---|
| A01 | 4 Sekunden ein, 6 Sekunden aus. Ein paar Mal. | Breathe in for 4 seconds, out for 6. A few times. | | | eigen |
| A02 | Atme durch die Nase ein und langsam durch den Mund aus. | Breathe in through your nose and slowly out through your mouth. | | | eigen |
| A03 | Drei ruhige Atemzüge. Mehr braucht es nicht. | Three calm breaths. That's all it takes. | | | eigen |
| A04 | 4 Sekunden ein, 4 halten, 4 aus, 4 halten. | In for 4 seconds, hold for 4, out for 4, hold for 4. | | | eigen |
| A05 | Leg eine Hand auf den Bauch und spür, wie er sich hebt und senkt. | Place a hand on your belly and feel it rise and fall. | | | eigen |
| A06 | Zähl beim Ausatmen langsam bis fünf. | Count slowly to five as you breathe out. | | | eigen |
| A07 | Lass das Ausatmen etwas länger sein als das Einatmen. | Let each breath out be a little longer than the breath in. | | | eigen |
| A08 | Einatmen: Ich bin hier. Ausatmen: Es ist genug Zeit. | Breathing in: I am here. Breathing out: there is enough time. | | | eigen |
| A09 | Ein tiefer Atemzug in den Bauch, dann ein langes Seufzen. | One deep breath into your belly, then a long sigh. | | | eigen |
