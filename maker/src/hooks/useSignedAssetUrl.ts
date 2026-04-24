import { useEffect, useState, useRef } from 'react';
import api from '../api';

/**
 * Resolve a single asset path to a signed URL usable in `<img src>` or
 * `<audio src>`. Returns an empty string until the sign call resolves,
 * which matches the prior `assetUrl` behavior where consumers render a
 * "Missing Asset" placeholder while the image is loading.
 *
 * `cacheKey` is embedded into the signed path as `?v=` so a bump forces
 * a fresh sign → fresh URL → browser refetch (bypassing `<img>` memory
 * cache).
 */
export function useSignedAssetUrl(fullApiPath: string, cacheKey: number = 0): string {
  const [signed, setSigned] = useState('');
  // Track latest request to avoid a stale response stomping on a newer one.
  const reqId = useRef(0);

  useEffect(() => {
    if (!fullApiPath) {
      setSigned('');
      return;
    }
    const pathToSign = cacheKey > 0 ? `${fullApiPath}?v=${cacheKey}` : fullApiPath;
    const myReq = ++reqId.current;
    api.signAssetUrls([pathToSign])
      .then((urls) => {
        if (myReq !== reqId.current) return; // superseded
        setSigned(urls[pathToSign] || '');
      })
      .catch(() => {
        if (myReq !== reqId.current) return;
        setSigned('');
      });
  }, [fullApiPath, cacheKey]);

  return signed;
}

/**
 * Bulk version: resolves many paths in one round-trip. Returns a map of
 * input path → signed URL (or empty string while loading/missing).
 */
export function useSignedAssetUrls(
  fullApiPaths: string[],
  cacheKey: number = 0,
): Record<string, string> {
  const [urls, setUrls] = useState<Record<string, string>>({});
  const reqId = useRef(0);

  // Join once per render; paths array identity shouldn't retrigger if
  // the contents are stable.
  const joined = fullApiPaths.join('|');

  useEffect(() => {
    if (fullApiPaths.length === 0) {
      setUrls({});
      return;
    }
    const toSign = cacheKey > 0
      ? fullApiPaths.map((p) => `${p}?v=${cacheKey}`)
      : fullApiPaths;
    const myReq = ++reqId.current;
    api.signAssetUrls(toSign)
      .then((result) => {
        if (myReq !== reqId.current) return;
        // Re-map back to the caller-provided paths (strip the ?v=)
        const out: Record<string, string> = {};
        for (let i = 0; i < fullApiPaths.length; i++) {
          const inputPath = fullApiPaths[i];
          const signedPath = toSign[i];
          if (result[signedPath]) out[inputPath] = result[signedPath];
        }
        setUrls(out);
      })
      .catch(() => {
        if (myReq !== reqId.current) return;
        setUrls({});
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [joined, cacheKey]);

  return urls;
}
