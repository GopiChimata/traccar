/*
 * Copyright 2010 Anton Tananaev (anton@tananaev.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.protocol.gl200;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;
import org.traccar.Position;
import org.traccar.DataManager;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;

/**
 * GL200 tracker protocol decoder
 */
public class Gl200ProtocolDecoder extends OneToOneDecoder {

    /**
     * Data manager
     */
    private DataManager dataManager;

    /**
     * Reset connection delay
     */
    private Integer resetDelay;

    /**
     * Init device table
     */
    public Gl200ProtocolDecoder(DataManager dataManager, Integer resetDelay) {
        this.dataManager = dataManager;
        this.resetDelay = resetDelay;
    }

    /**
     * Regular expressions pattern
     */
    static private Pattern pattern = Pattern.compile(
            "\\+RESP:GT...," +
            "\\d{6}," +                         // Protocol version
            "(\\d{15})," +                      // IMEI
            "[^,]*," +                          // Device name
            "\\d," +                            // Report ID
            "\\d," +                            // Report type
            "\\d*," +                           // Number
            "(\\d*)," +                         // GPS accuracy
            "(\\d+.\\d)," +                     // Speed
            "(\\d+)," +                         // Course
            "(-?\\d+.\\d)," +                   // Altitude
            "(-?\\d+.\\d+)," +                  // Longitude
            "(-?\\d+.\\d+)," +                  // Latitude
            "(\\d{4})(\\d{2})(\\d{2})" +        // Date (YYYYMMDD)
            "(\\d{2})(\\d{2})(\\d{2})," +       // Time (HHMMSS)
            "[^$]*");

    /**
     * Decode message
     */
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {

        String sentence = (String) msg;

        // Parse message
        Matcher parser = pattern.matcher(sentence);
        if (!parser.matches()) {
            return null;
        }

        // Create new position
        Position position = new Position();

        Integer index = 1;

        // Get device by IMEI
        String imei = parser.group(index++);
        position.setDeviceId(dataManager.getDeviceByImei(imei).getId());
        
        // Validity
        position.setValid(Integer.valueOf(parser.group(index++)) == 0 ? false : true);
        
        // Position info
        position.setSpeed(Double.valueOf(parser.group(index++)));
        position.setCourse(Double.valueOf(parser.group(index++)));
        position.setAltitude(Double.valueOf(parser.group(index++)));
        position.setLongitude(Double.valueOf(parser.group(index++)));
        position.setLatitude(Double.valueOf(parser.group(index++)));
        
        // Date
        Calendar time = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        time.clear();
        time.set(Calendar.YEAR, Integer.valueOf(parser.group(index++)));
        time.set(Calendar.MONTH, Integer.valueOf(parser.group(index++)) - 1);
        time.set(Calendar.DAY_OF_MONTH, Integer.valueOf(parser.group(index++)));

        // Time
        time.set(Calendar.HOUR, Integer.valueOf(parser.group(index++)));
        time.set(Calendar.MINUTE, Integer.valueOf(parser.group(index++)));
        time.set(Calendar.SECOND, Integer.valueOf(parser.group(index++)));
        position.setTime(time.getTime());

        return position;
    }

    /**
     * Disconnect channel
     */
    class DisconnectTask extends TimerTask {
        private Channel channel;
        
        public DisconnectTask(Channel channel) {
            this.channel = channel;
        }
        
        public void run() {
            channel.disconnect();
        } 
    }

    /**
     * Handle connect event
     */
    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent evt) throws Exception {
        super.handleUpstream(ctx, evt);

        if (evt instanceof ChannelStateEvent) {
            ChannelStateEvent event = (ChannelStateEvent) evt;

            if (event.getState() == ChannelState.CONNECTED && event.getValue() != null && resetDelay != 0) {
                new Timer().schedule(new DisconnectTask(evt.getChannel()), resetDelay);
            }
        }
    }

}