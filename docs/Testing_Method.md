# Frame Corruption Simulation



## Frame Drop



Frame drop is created by manually removing selected frames from the original video sequence without adjusting presentation timestamps.



### Process:

1\. Select specific frame indices.

2\. Remove those frames from the video stream.

3\. Preserve original timing structure (no timestamp reset).



### Result:

\- Frame count decreases.

\- Timestamp gaps appear.

\- Playback shows sudden motion jumps.

\- Video becomes variable frame rate (VFR).



### Behavior:

Time is skipped. Motion discontinuity occurs.





---



## Frame Merge



Frame merge is created by duplicating the pixel content of a frame across multiple consecutive frames while keeping timestamps continuous.



### Process:

1\. Select a frame to duplicate.

2\. Replace subsequent frames with identical copies.

3\. Maintain uniform timestamp spacing.



### Result:

\- Frame count remains unchanged.

\- No timestamp gaps.

\- Consecutive frames contain identical pixel data.

\- Playback shows brief motion freeze.



### Behavior:

Time is preserved. Motion temporarily stalls.





---



## Detection Strategy



### Frame Drop Detection:

\- Monitor timestamp intervals.

\- If frame delta exceeds expected interval → drop detected.



### Frame Merge Detection:

\- Compare pixel similarity between consecutive frames.

\- If pixel difference is near zero → merge detected.



### Created Sample Videos:

Drive Link: https://drive.google.com/drive/folders/1dNZjY4i_guTFjnjFLrBszetA3MHFSOxx


