/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *	Popp Solar Siren
 *
 *	Author: SmartThings
 *	Date: 2017-07-19
 */

metadata {
 definition (name: "Popp Solar Siren", namespace: "raerni", author: "Roman Aerni", ocfDeviceType: "x.com.st.d.sensor.smoke") {
	capability "Actuator"
	capability "Temperature Measurement"
	capability "Alarm"
	capability "Switch"
	capability "Health Check"
	capability "Battery"
  capability "Refresh"
  capability "Tamper Alert"
  capability "Health Check"

	command "test"
  command "off"
  command "panic"

	fingerprint deviceId: "0x1005", inClusters: "0x5E,0x98", deviceJoinName: "Popp Solar Siren"
 }

 simulator {
	// reply messages
	reply "9881002001FF,9881002002": "command: 9881, payload: 002003FF"
	reply "988100200100,9881002002": "command: 9881, payload: 00200300"
	reply "9881002001FF,delay 3000,988100200100,9881002002": "command: 9881, payload: 00200300"
 }

 tiles(scale: 2) {
	multiAttributeTile(name:"alarm", type: "generic", width: 6, height: 4){
		tileAttribute ("device.alarm", key: "PRIMARY_CONTROL") {
			attributeState "off", label:'off', action:"on", icon:"st.alarm.alarm.alarm", backgroundColor:"#ffffff" //action:'alarm.siren'
			attributeState "both", label:'alarm!', action:"off", icon:"st.alarm.alarm.alarm", backgroundColor:"#e86d13" //action:'alarm.off'
		}
        tileAttribute ("statusText", key: "SECONDARY_CONTROL") {
        	attributeState "statusText", label:'${currentValue}'
		}

	}
	standardTile("panic", "device.alarm", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
		state "default", label:'', action:"alarm.siren", icon:"st.custom.sonos.unmuted" //icon:"st.secondary.on"
	}
	standardTile("off", "device.alarm", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
		state "default", label:'', action:"alarm.off", icon:"st.custom.sonos.muted"
	}
	standardTile("test", "device.alarm", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
		state "default", label:'', action:"test", icon:"st.secondary.test"
	}
    standardTile("tamper", "device.tamper", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
		state("clear", label:'clear', icon:"st.contact.contact.closed", backgroundColor:"#cccccc", action: "resetTamperAlert")
        state("detected", label:'tamper', icon:"st.contact.contact.open", backgroundColor:"#e86d13", action: "resetTamperAlert")
	}
	valueTile("battery", "device.battery", inactiveLabel: false, width: 2, height: 2) {
		state "battery", label:'${currentValue}% battery', unit:""
	}
    valueTile("temperature", "device.temperature", inactiveLabel: false, width: 2, height: 2) {
		state "temperature", label:'${currentValue}°', backgroundColors:[
                	[value: 0, color: "#153591"],
                    [value: 7, color: "#1e9cbb"],
                    [value: 15, color: "#90d2a7"],
					[value: 23, color: "#44b621"],
					[value: 29, color: "#f1d801"],
					[value: 35, color: "#d04e00"],
					[value: 38, color: "#bc2323"]
				]
            }
	}

preferences {
		input "sound", "number", title: "Siren sound (1-5)", defaultValue: 1, required: true //, displayDuringSetup: true  // don't display during setup until defaultValue is shown
		input "volume", "number", title: "Volume (1-3)", defaultValue: 3, required: true //, displayDuringSetup: true
        input "mode", "number", title:"Siren triggering mode", defaultValue: 0, required: true
        input "delay", "number", title:"Siren triggering delay", defaultValue: 0, required: true // set a Delay befor the siren goes off
	}

	main "alarm"
	details(["alarm", "panic", "test", "off", "battery", "temperature", "tamper"])
 }
//}

def installed() {
// Device-Watch simply pings if no device events received for 32min(checkInterval)
	sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])

	response(secure(zwave.basicV1.basicGet()))
}

def updated() {
// Device-Watch simply pings if no device events received for 32min(checkInterval)
	sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])

	if(!state.sound) state.sound = 1
	if(!state.volume) state.volume = 3

	log.debug "settings: ${settings.inspect()}, state: ${state.inspect()}"

	Short sound = (settings.sound as Short) ?: 1
	Short volume = (settings.volume as Short) ?: 3

	if (sound != state.sound || volume != state.volume) {
		state.sound = sound
		state.volume = volume
		return response([
			secure(zwave.configurationV1.configurationSet(parameterNumber: 37, size: 2, configurationValue: [sound, volume])),
			"delay 1000",
			secure(zwave.basicV1.basicSet(value: 0x00)),
		])
	}
}

def parse(String description) {
	log.debug "parse($description)"
	def result = null
	def cmd = zwave.parse(description, [0x98: 1, 0x20: 1, 0x70: 1])
	if (cmd) {
		result = zwaveEvent(cmd)
	}
	log.debug "Parse returned ${result?.inspect()}"
	return result
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand([0x20: 1, 0x85: 2, 0x70: 1])
	// log.debug "encapsulated: $encapsulatedCommand"
	if (encapsulatedCommand) {
		zwaveEvent(encapsulatedCommand)
	}
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	log.debug "rx $cmd"
	[
		createEvent([name: "switch", value: cmd.value ? "on" : "off", displayed: false]),
		createEvent([name: "alarm", value: cmd.value ? "both" : "off"])
	]
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	createEvent(displayed: false, descriptionText: "$device.displayName: $cmd")
}

def on() {
	log.debug "sending on"
	[
		"delay 3000",
        secure(zwave.basicV1.basicSet(value: 0xFF)),
		secure(zwave.basicV1.basicGet())
	]
}

def off() {
	log.debug "sending off"
	[
		secure(zwave.basicV1.basicSet(value: 0x00)),
		secure(zwave.basicV1.basicGet())
	]
}

def strobe() {
	on()
}

def siren() {
	on()
}

def both() {
	on()
}

def test() {
	[
		secure(zwave.basicV1.basicSet(value: 0xFF)),
		"delay 3000",
		secure(zwave.basicV1.basicSet(value: 0x00)),
		secure(zwave.basicV1.basicGet())
	]
}


private getAdjustedTemp(value) {
    
    value = Math.round((value as Double) * 100) / 100

	if (settings."201") {
	   return value =  value + Math.round(settings."201" * 100) /100
	} else {
       return value
    }
    
}


def generate_preferences(configuration_model)
{
    def configuration = parseXml(configuration_model)
   
    configuration.Value.each
    {
        switch(it.@type)
        {   
            case ["byte","short","four"]:
                input "${it.@index}", "number",
                    title:"${it.@label}\n" + "${it.Help}",
                    range: "${it.@min}..${it.@max}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}"
            break
            case "list":
                def items = []
                it.Item.each { items << ["${it.@value}":"${it.@label}"] }
                input "${it.@index}", "enum",
                    title:"${it.@label}\n" + "${it.Help}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}",
                    options: items
            break
            case "decimal":
               input "${it.@index}", "decimal",
                    title:"${it.@label}\n" + "${it.Help}",
                    range: "${it.@min}..${it.@max}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}"
            break
            case "boolean":
               input "${it.@index}", "boolean",
                    title:"${it.@label}\n" + "${it.Help}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}"
            break
        }  
    }
}

private getBatteryRuntime() {
   def currentmillis = now() - state.batteryRuntimeStart
   def days=0
   def hours=0
   def mins=0
   def secs=0
   secs = (currentmillis/1000).toInteger() 
   mins=(secs/60).toInteger() 
   hours=(mins/60).toInteger() 
   days=(hours/24).toInteger() 
   secs=(secs-(mins*60)).toString().padLeft(2, '0') 
   mins=(mins-(hours*60)).toString().padLeft(2, '0') 
   hours=(hours-(days*24)).toString().padLeft(2, '0') 
 

  if (days>0) { 
      return "$days days and $hours:$mins:$secs"
  } else {
      return "$hours:$mins:$secs"
  }
}

def resetBatteryRuntime() {
    if (state.lastReset != null && now() - state.lastReset < 5000) {
        logging("Reset Double Press")
        state.batteryRuntimeStart = now()
        updateStatus()
    }
    state.lastReset = now()
}

private updateStatus(){
   def result = []
   if(state.batteryRuntimeStart != null){
        sendEvent(name:"batteryRuntime", value:getBatteryRuntime(), displayed:false)
        if (device.currentValue('currentFirmware') != null){
            sendEvent(name:"statusText2", value: "Firmware: v${device.currentValue('currentFirmware')} - Battery: ${getBatteryRuntime()} Double tap to reset", displayed:false)
        } else {
            sendEvent(name:"statusText2", value: "Battery: ${getBatteryRuntime()} Double tap to reset", displayed:false)
        }
    } else {
        state.batteryRuntimeStart = now()
    }

    String statusText = ""
    if(device.currentValue('humidity') != null)
        statusText = "RH ${device.currentValue('humidity')}% - "
    if(device.currentValue('illuminance') != null)
        statusText = statusText + "LUX ${device.currentValue('illuminance')} - "
    if(device.currentValue('ultravioletIndex') != null)
        statusText = statusText + "UV ${device.currentValue('ultravioletIndex')} - "
        
    if (statusText != ""){
        statusText = statusText.substring(0, statusText.length() - 2)
        sendEvent(name:"statusText", value: statusText, displayed:false)
    }
}

private secure(physicalgraph.zwave.Command cmd) {
	zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
	secure(zwave.basicV1.basicGet())
}

private def logging(message) {
    if (state.enableDebugging == null || state.enableDebugging == "true") log.debug "$message"
}

/*
def configuration_model()
{
'''
<configuration>
    <Value type="list" index="101" label="How can the temper sensor be reset?" min="0" max="2" value="0" byteSize="1" setting_type="zwave" displayDuringSetup="true">
    //  Parameter Number 1, Size 1
    <Help>
Sets the tamper triggering mode when removed from the holder.
Default: via Smartthings App
    </Help>
        <Item label="via Smartthings App" value="0" />
        <Item label="putt siren back" value="1" />
        <Item label="Enable Service Mode" value="2" />
  </Value>
    <Value type="list" index="101" label="Operating Mode?" min="0" max="2" value="2" byteSize="1" setting_type="zwave" displayDuringSetup="true"> 
    //  Parameter Number 5, Size 1
    <Help>
Sets the operating mode.
Default: Flash + Siren
    </Help>
        <Item label="Siren only" value="0" />
        <Item label="Flash only" value="1" />
        <Item label="Flash + Siren (Default)" value="2" />
  </Value>
	<Value type="decimal" byteSize="1" index="201" label="Temperature adjustment" min="*" max="*" value="">
    //  Parameter Number 2, Size 1
    <Help>
Positive correction steps: 1 – 127 to adjust +0.1°C per
Negative correction steps: 128 – 255 to adjust -0.1°C per
Default: 0
Note: 
1. The calibration value = correct value - measure value.
E.g. If measure value =22.0°C and the correct value = 21.5°C, so the calibration value = 21.5°C - 22.0°C = -0.5°C >> 0.5/0.1 = 5 >> 128 + (5-1) >> Value = 132.
If the measure value =21.1°C and the correct value = 21.5°C, so the calibration value = 21.5°C - 21.1°C = 0.4°C >> 0.4/0.1 = 4 >> 1 + (4-1) >> Value = 4. 
    </Help>
  </Value> 
	<Value type="list" index="40" label="Enable selective reporting?" min="0" max="1" value="0" byteSize="1" setting_type="zwave" fw="1.06,1.07,1.08,1.06EU,1.07EU">
    <Help>
    </Help>
        <Item label="No" value="0" />
        <Item label="Yes" value="1" />
  </Value>
  <Value type="short" byteSize="2" index="41" label="Temperature Threshold" min="1" max="5000" value="20" setting_type="zwave" fw="1.06,1.07,1.08,1.06EU,1.07EU">
    <Help>
Threshold change in temperature to induce an automatic report.
Range: 1~5000.
Default: 20
Note:
Only used if selective reporting is enabled.
1. The unit is Fahrenheit for US version, Celsius for EU/AU version.
2. The value contains one decimal point. E.g. if the value is set to 20, the threshold value =2.0 ℃ (EU/AU version) or 2.0 ℉ (US version). When the current temperature gap is more then 2.0, which will induce a temperature report to be sent out.
    </Help>
  </Value>
  <Value type="byte" byteSize="1" index="44" label="Battery Threshold" min="1" max="99" value="10" setting_type="zwave" fw="1.06,1.07,1.08,1.06EU,1.07EU">
    <Help>
Threshold change in battery level to induce an automatic report.
Range: 1~99.
Default: 10
Note:
Only used if selective reporting is enabled.
1. The unit is %.
2. The default value is 10, which means that if the current battery level gap is more than 10%, it will send out a battery report.
    </Help>
  </Value>

  <Value type="boolean" index="enableDebugging" label="Enable Debug Logging?" value="true" setting_type="preference" fw="1.06,1.07,1.08,1.06EU,1.07EU">
    <Help>

    </Help>
  </Value>
</configuration>
...
}
*/
