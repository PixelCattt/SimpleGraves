<div align="center">
  <img src="https://github.com/Flummidill/SimpleGraves/blob/HEAD/icons/SimpleGraves-250x250.png?raw=true" alt="SimpleGraves-Icon">
  
  <h1>SimpleGraves</h1>
  <a href="https://modrinth.com/plugin/simple_graves/versions">
    <img src="https://img.shields.io/modrinth/v/simple_graves?style=for-the-badge&label=Version&color=5A00FF">
    <img src="https://img.shields.io/modrinth/dt/simple_graves?style=for-the-badge&label=Downloads&color=29A100">
  </a>
</div>


## 🎯 Features
- **Grave System for Players**
When players die, their items and XP are safely stored in a Grave so they don't despawn.

- **Grave-Info Command**
Players can easily check the location of their graves, helping them recover items without wandering aimlessly.

- **Admin Grave Management**
Admins can list, inspect, teleport to, or remove graves. This makes it easy to help players or keep the world clean from abandoned graves.

- **Configurable Grave Behavior**
Decide whether Graves can be looted by other players, how much XP gets stored in them, and more.


<hr/>


### Commands:
```
/graveinfo <number> - Shows the Location of your Grave
```

### Admin Commands:
```
/graveadmin go <player> <number> - Teleports you to a Player's Grave
/graveadmin list <player> - Lists a Player's Graves
/graveadmin info <player> <number> - Shows the Location of a Player's Grave
/graveadmin remove <player> <number> - Removes a Player's Grave
```

### Permissions:
```
simplegraves.use (Default: true) - Allow use of Player Commands
simplegraves.admin (Default: false) - Allow use of Admin Commands
```

### Config:
```
grave-stealing - Allows breaking other Player's Graves
max-stored-xp - Maximum Amount of XP-Levels that will be stord in Graves
delete-vanishing-items - Deletes Items with Curse of Vanishing
non-grave-player-head-water-protection - Protect Player Heads that aren't a Grave from being destroyed by Water (BIG Performance Improvement if Enabled)
```