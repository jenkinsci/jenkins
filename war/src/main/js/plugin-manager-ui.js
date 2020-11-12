import pluginManagerAvailable from './templates/plugin-manager/helloWorld.hbs'

console.log('are you here??')
document.addEventListener("DOMContentLoaded", function() {
    console.log('I got called');
    
    var div = document.createElement('div');
    div.innerHTML = pluginManagerAvailable({
        name: 'jonny'
    });
    
    console.log(div)
    document.body.appendChild(div);
});
