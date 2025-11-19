package it.pagopa.pn.papertracker.mapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class SmartMapper {

    private static final ModelMapper modelMapper;

    public static <S,T> T mapToClass(S source, Class<T> destinationClass ){
        T result;
        if( source != null) {
            result = modelMapper.map(source, destinationClass );
        } else {
            result = null;
        }
        return result;
    }

    static{
        modelMapper = new ModelMapper();
        modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
    }

}


