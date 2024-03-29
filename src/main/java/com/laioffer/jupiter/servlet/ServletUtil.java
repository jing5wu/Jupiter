package com.laioffer.jupiter.servlet;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laioffer.jupiter.entity.Item;
import org.apache.commons.codec.digest.DigestUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ServletUtil {
    public static <T> void writeData(HttpServletResponse response,
                                     T data)
    throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().print(
                new ObjectMapper().writeValueAsString(data));
    }

    // help encrypt user password before saving to the database
    public static String encryptPassword
    (String userId, String password) throws IOException {
        return DigestUtils.md5Hex(
                userId + DigestUtils.md5Hex(password)
        ).toLowerCase(Locale.ROOT);
    }

    public static <T> T readRequestBody(
            Class<T> cl, HttpServletRequest request) throws IOException{
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(request.getReader(), cl);
        } catch (JsonParseException | JsonMappingException e) {
            return null;
        }
    }
}
