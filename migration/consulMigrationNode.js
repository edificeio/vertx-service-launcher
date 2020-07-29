//node consulMigrationNode.js BASE_SCOPE SCOPE PARENT_SCOPE CONFIG_FILE HOST
var fs = require("fs")
var fetch = require("node-fetch");

var myArgs = process.argv.slice(2);
if (myArgs.length != 5) {
    console.error("bad arguments: (pf) (base) (baseInheritance) (file) ", myArgs)
}
var BASE = `http://${myArgs[4]}/v1/kv`;

async function run() {
    await removeExistingKeys();
    await createNewKeys();
}

var cache = null;
async function createIfOverride(name, value, callback) {
    if (cache == null) {
        var folder = `${BASE}/service/${myArgs[0]}/${myArgs[2]}`;
        var resKeys = await fetch(`${folder}?recurse`)
        cache = await resKeys.json();
    }
    var value64 = Buffer.from(value).toString('base64')
    for (var s of cache) {
        if (s.Key.endsWith(name)) {
            if (s.Value == value64) {
                console.log(`Key ${s.Key} already exists`)
                return;
            } else {
                console.log(`Key ${s.Key} is overriden`)
                break;
            }
        }
    }
    callback();
}

async function removeExistingKeys() {
    var mainKey = `service/${myArgs[0]}/${myArgs[1]}`;
    var folder = `${BASE}/${mainKey}`;
    console.log("cleaning folder: ", folder)
    var resKeys = await fetch(`${folder}?keys`)
    if(resKeys.status==404){
        console.log('folder does not exists : ',folder)
        return;//folder not exists
    }
    var keys = await resKeys.json();
    for (var key of keys) {
        if (key != mainKey && key != `${mainKey}/`) {
            console.log("delete key: ", key)
            await fetch(`${BASE}/${key}`, {
                "method": "DELETE",
            });
        }
    }
}

async function createNewKeys() {
    var folder = `${BASE}/service/${myArgs[0]}/${myArgs[1]}`;
    var index = 1;
    var config = getConfig();
    for (var service of config.services) {
        if (service.name) {
            var splits = service.name.split("~")
            var newName = ("00" + index).slice(-3) + "~" + splits[1];
            console.log("try create service key: ", newName)
            var value = JSON.stringify(service);
            (function(newName, value){
                createIfOverride(newName, value, async function () {
                    console.log("create service key: ", newName)
                    var res = await fetch(`${folder}/${newName}`, {
                        "body": value,
                        "method": "PUT",
                    });
                })
            })(newName, value);
            index++;
        }
    }
}
function getConfig() {
    var buffer = fs.readFileSync(myArgs[3]).toString()
    return JSON.parse(buffer);
}

run();