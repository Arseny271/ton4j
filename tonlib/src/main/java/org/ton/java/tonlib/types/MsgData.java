package org.ton.java.tonlib.types;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Builder
@Setter
@Getter
@ToString
public class MsgData implements Serializable {

    @SerializedName("@type")
    final String type;
    String body;
    String text;
    String init_state;

}

