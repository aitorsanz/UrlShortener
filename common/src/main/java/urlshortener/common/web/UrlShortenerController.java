package urlshortener.common.web;

import com.google.common.hash.Hashing;

import org.apache.commons.validator.routines.UrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.util.LinkedList;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import urlshortener.common.domain.ShortURL;
import urlshortener.common.repository.ClickRepository;
import urlshortener.common.repository.ShortURLRepository;
import urlshortener.common.domain.Click;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@RestController
public class UrlShortenerController {
	private static final Logger LOG = LoggerFactory
			.getLogger(UrlShortenerController.class);
	@Autowired
	protected ShortURLRepository shortURLRepository;

	@Autowired
	protected ClickRepository clickRepository;

	@RequestMapping(value = "/{id:(?!link-single|link-multi).*}", method = RequestMethod.GET)
	public ResponseEntity<?> redirectTo(@PathVariable String id,
			HttpServletRequest request) {
		ShortURL l = shortURLRepository.findByKey(id);
		if (l != null) {
			createAndSaveClick(id, extractIP(request));
			return createSuccessfulRedirectToResponse(l);
		} else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}

	private void createAndSaveClick(String hash, String ip) {
		Click cl = new Click(null, hash, new Date(System.currentTimeMillis()),
				null, null, null, ip, null);
		cl=clickRepository.save(cl);
		LOG.info(cl!=null?"["+hash+"] saved with id ["+cl.getId()+"]":"["+hash+"] was not saved");
	}

	private String extractIP(HttpServletRequest request) {
		return request.getRemoteAddr();
	}

	private ResponseEntity<?> createSuccessfulRedirectToResponse(ShortURL l) {
		HttpHeaders h = new HttpHeaders();
		h.setLocation(URI.create(l.getTarget()));
		return new ResponseEntity<>(h, HttpStatus.valueOf(l.getMode()));
	}

	@RequestMapping(value = "/link-single", method = RequestMethod.POST)
	public ResponseEntity<ShortURL> singleShortener(@RequestParam("url") String url,
											  @RequestParam(value = "sponsor", required = false) String sponsor,
											  HttpServletRequest request) {
		ShortURL su = createAndSaveIfValid(url, sponsor, UUID
				.randomUUID().toString(), extractIP(request));
		
		if (su != null) {
			HttpHeaders h = new HttpHeaders();
			h.setLocation(su.getUri());
			
			return new ResponseEntity<>(su, h, HttpStatus.CREATED);
		} else {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
	}
	
	@RequestMapping(value = "/link-multi", method = RequestMethod.POST)
    public ResponseEntity<ShortURL[]> multiShortener(@RequestParam("url") MultipartFile csvFile,
                                              @RequestParam(value = "sponsor", required = false) String sponsor,
                                              HttpServletRequest request) {
	    
	    LinkedList<String> urls = processFile(csvFile);
	    
	    ShortURL[] su = new ShortURL[urls.size()];
	    
	    if(validateUrlList(urls)){
	        int i = 0;
	        
	        for(String url : urls){
	            su[i] = createAndSaveIfValid(url, sponsor, UUID
	                    .randomUUID().toString(), extractIP(request));
	            i++;
	        }
	        
	        HttpHeaders h = new HttpHeaders();
            return new ResponseEntity<>(su, h, HttpStatus.CREATED);
	    } else {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
	
    /**
     * Returns all the comma-separated URLs contained in the CSV file
     */
	private LinkedList<String> processFile(MultipartFile csvFile){
	    
	    InputStream is;
	    
        try {
            is = csvFile.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            
            String line;
            LinkedList<String> list = new LinkedList<String>();
            while ((line = br.readLine()) != null) {
                String[] urls = line.split(",");
                for(String url : urls){
                    if(!url.trim().equals("")){
                        list.addLast(url);
                    }
                }
            }
            
            return list;
        } catch (IOException e) {
            return null;
        }
	}
	
	/**
	 * Returns true if, and only if, all URLs are valid
	 */
	private boolean validateUrlList(LinkedList<String> urls){
	    
	    boolean resp = true;
	    
	    for(String url : urls){
	        resp &= isValid(url);
	    }
	    
	    return resp;
	}

	private ShortURL createAndSaveIfValid(String url, String sponsor,
										  String owner, String ip) {
		if (isValid(url)) {
			String id = Hashing.murmur3_32()
					.hashString(url, StandardCharsets.UTF_8).toString();
			ShortURL su = new ShortURL(id, url,
					linkTo(
							methodOn(UrlShortenerController.class).redirectTo(
									id, null)).toUri(), sponsor, new Date(
							System.currentTimeMillis()), owner,
					HttpStatus.TEMPORARY_REDIRECT.value(), true, ip, null);
			return shortURLRepository.save(su);
		} else {
			return null;
		}
	}
	
    private boolean isValid(String url) {
        UrlValidator urlValidator = new UrlValidator(new String[] { "http", "https" });
        return urlValidator.isValid(url);
    }
}
